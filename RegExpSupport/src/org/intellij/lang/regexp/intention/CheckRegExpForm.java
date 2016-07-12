/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.RegExpModifierProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class CheckRegExpForm {
  private static final String LAST_EDITED_REGEXP = "last.edited.regexp";

  private static final JBColor BACKGROUND_COLOR_MATCH = new JBColor(0xe7fadb, 0x445542);
  private static final JBColor BACKGROUND_COLOR_NOMATCH = new JBColor(0xffb1a0, 0x6e2b28);

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
    final String sampleText = PropertiesComponent.getInstance(myProject).getValue(LAST_EDITED_REGEXP, "Sample Text");
    mySampleText = new EditorTextField(sampleText, myProject, PlainTextFileType.INSTANCE) {
      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };
    mySampleText.setOneLineMode(false);
    int preferredWidth = Math.max(JBUI.scale(250), myRegExp.getPreferredSize().width);
    myRegExp.setPreferredWidth(preferredWidth);
    mySampleText.setPreferredWidth(preferredWidth);

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

        final Alarm updater = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
        DocumentAdapter documentListener = new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent e) {
            updater.cancelAllRequests();
            if (!updater.isDisposed()) {
              updater.addRequest(CheckRegExpForm.this::updateBalloon, 200);
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
    myRootPanel.setBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));
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
    final Boolean correct = isMatchingText(myRegexpFile, mySampleText.getText());

    ApplicationManager.getApplication().invokeLater(() -> {
      mySampleText.setBackground(correct != null && correct ? BACKGROUND_COLOR_MATCH : BACKGROUND_COLOR_NOMATCH);
      myMessage.setText(correct == null ? "Pattern is too complex" : correct ? "Matches!" : "No match");
      myRootPanel.revalidate();
      Balloon balloon = JBPopupFactory.getInstance().getParentBalloonFor(myRootPanel);
      if (balloon != null) balloon.revalidate();
    }, ModalityState.current());
  }

  @TestOnly
  public static boolean isMatchingTextTest(@NotNull PsiFile regexpFile, @NotNull String sampleText) {
    Boolean result = isMatchingText(regexpFile, sampleText);
    return result != null && result;
  }

  private static Boolean isMatchingText(@NotNull final PsiFile regexpFile, @NotNull String sampleText) {
    final String regExp = regexpFile.getText();

    Integer patternFlags = ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
      @Override
      public Integer compute() {
        PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(regexpFile);
        int flags = 0;
        if (host != null) {
          for (RegExpModifierProvider provider : RegExpModifierProvider.EP.allForLanguage(host.getLanguage())) {
            flags = provider.getFlags(host, regexpFile);
            if (flags > 0) break;
          }
        }
        return flags;
      }
    });

    try {
      //noinspection MagicConstant
      return Pattern.compile(regExp, patternFlags).matcher(StringUtil.newBombedCharSequence(sampleText, 1000)).matches();
    } catch (ProcessCanceledException pc) {
      return null;
    }
    catch (Exception ignore) {}

    return false;
  }
}
