/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.intention;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.RegExpModifierProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class CheckRegExpForm {
  private static final String LAST_EDITED_REGEXP = "last.edited.regexp";

  private static final JBColor BACKGROUND_COLOR_MATCH = new JBColor(new Color(231, 250, 219), new Color(68, 85, 66));
  private static final JBColor BACKGROUND_COLOR_NOMATCH = new JBColor(new Color(255, 177, 160), new Color(110, 43, 40));

  private final PsiFile myRegexpFile;

  private EditorTextField mySampleText; //TODO[kb]: make it multiline

  private EditorTextField myRegExp;
  private JPanel myRootPanel;
  private JBLabel myMessage;
  private Project myProject;


  public CheckRegExpForm(@NotNull PsiFile regexpFile) {
    myRegexpFile = regexpFile;
  }

  private void createUIComponents() {
    myProject = myRegexpFile.getProject();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(myRegexpFile);

    myRegExp = new EditorTextField(document, myProject, RegExpLanguage.INSTANCE.getAssociatedFileType());
    myRegExp.setPreferredWidth(Math.max(300, myRegExp.getPreferredSize().width));
    final String sampleText = PropertiesComponent.getInstance(myProject).getValue(LAST_EDITED_REGEXP, "Sample Text");
    mySampleText = new EditorTextField(sampleText, myProject, PlainTextFileType.INSTANCE);
    mySampleText.setBorder(
      new CompoundBorder(new EmptyBorder(2, 2, 2, 4), new LineBorder(UIUtil.isUnderDarcula() ? Gray._100 : JBColor.border())));
    mySampleText.setOneLineMode(false);

    myRootPanel = new JPanel(new BorderLayout()) {
      Disposable disposable;

      @Override
      public void addNotify() {
        super.addNotify();
        disposable = Disposer.newDisposable();

        IdeFocusManager.getGlobalInstance().requestFocus(mySampleText, true);

        new AnAction(){
          @Override
          public void actionPerformed(AnActionEvent e) {
            IdeFocusManager.findInstance().requestFocus(myRegExp.getFocusTarget(), true);
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), mySampleText);

        final Alarm updater = new Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable);
        DocumentAdapter documentListener = new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent e) {
            updater.cancelAllRequests();
            if (!updater.isDisposed()) {
              updater.addRequest(new Runnable() {
                @Override
                public void run() {
                  updateBalloon();
                }
              }, 200);
            }
          }
        };
        myRegExp.addDocumentListener(documentListener);
        mySampleText.addDocumentListener(documentListener);

        updateBalloon();
        mySampleText.selectAll();
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        Disposer.dispose(disposable);
        PropertiesComponent.getInstance(myProject).setValue(LAST_EDITED_REGEXP, mySampleText.getText());
      }
    };
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return mySampleText;
  }

  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  private void updateBalloon() {
    boolean correct = isMatchingText(myRegexpFile, mySampleText.getText());

    mySampleText.setBackground(correct ? BACKGROUND_COLOR_MATCH : BACKGROUND_COLOR_NOMATCH);
    myMessage.setText(correct ? "Matches!" : "no match");
    myRootPanel.revalidate();
  }

  @TestOnly
  public static boolean isMatchingTextTest(@NotNull PsiFile regexpFile, @NotNull String sampleText) {
    return isMatchingText(regexpFile, sampleText);
  }

  private static boolean isMatchingText(@NotNull PsiFile regexpFile, @NotNull String sampleText) {
    final String regExp = regexpFile.getText();

    PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(regexpFile);
    int flags = 0;
    if (host != null) {
      for (RegExpModifierProvider provider : RegExpModifierProvider.EP.allForLanguage(host.getLanguage())) {
        flags = provider.getFlags(host, regexpFile);
        if (flags > 0) break;
      }
    }
    try {
      return Pattern.compile(regExp, flags).matcher(sampleText).matches();
    } catch (Exception ignore) {}

    return false;
  }
}
