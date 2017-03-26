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
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionId;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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
import org.intellij.lang.regexp.*;
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

  private EditorTextField mySampleText;

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

    final Language language = myRegexpFile.getLanguage();
    final LanguageFileType fileType;
    if (language instanceof RegExpLanguage) {
      fileType = RegExpLanguage.INSTANCE.getAssociatedFileType();
    }
    else {
      // for correct syntax highlighting
      fileType = new RegExpFileType(language);
    }
    myRegExp = new EditorTextField(document, myProject, fileType);
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
      Alarm updater;

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

        updater = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
        DocumentAdapter documentListener = new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent e) {
            update();
          }
        };
        myRegExp.addDocumentListener(documentListener);
        mySampleText.addDocumentListener(documentListener);

        update();
        mySampleText.selectAll();
      }

      public void update() {
        final TransactionId transactionId = TransactionGuard.getInstance().getContextTransaction();
        updater.cancelAllRequests();
        if (!updater.isDisposed()) {
          updater.addRequest(() -> {
            final RegExpMatchResult result = isMatchingText(myRegexpFile, mySampleText.getText());
            TransactionGuard.getInstance().submitTransaction(myProject, transactionId, () -> setBalloonState(result));
          }, 200);
        }
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

  void setBalloonState(RegExpMatchResult result) {
    mySampleText.setBackground(result == RegExpMatchResult.MATCHES ? BACKGROUND_COLOR_MATCH : BACKGROUND_COLOR_NOMATCH);
    switch (result) {
      case MATCHES:
        myMessage.setText("Matches!");
        break;
      case NO_MATCH:
        myMessage.setText("No match");
        break;
      case TIMEOUT:
        myMessage.setText("Pattern is too complex");
        break;
      case BAD_REGEXP:
        myMessage.setText("Bad pattern");
        break;
    }
    myRootPanel.revalidate();
    Balloon balloon = JBPopupFactory.getInstance().getParentBalloonFor(myRootPanel);
    if (balloon != null && !balloon.isDisposed()) balloon.revalidate();
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return mySampleText;
  }

  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  @TestOnly
  public static boolean isMatchingTextTest(@NotNull PsiFile regexpFile, @NotNull String sampleText) {
    final RegExpMatchResult result = isMatchingText(regexpFile, sampleText);
    return result != null && result == RegExpMatchResult.MATCHES;
  }
  static RegExpMatchResult isMatchingText(@NotNull final PsiFile regexpFile, @NotNull String sampleText) {
    final String regExp = regexpFile.getText();

    final Language regexpFileLanguage = regexpFile.getLanguage();
    final RegExpMatcherProvider matcherProvider = RegExpMatcherProvider.EP.forLanguage(regexpFileLanguage);
    if (matcherProvider != null) {
      final RegExpMatchResult result = ReadAction.compute(() -> {
        final PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(regexpFile);
        if (host != null) {
          return matcherProvider.matches(regExp, regexpFile, host, sampleText, 1000L);
        }
        return null;
      });
      if (result != null) {
        return result;
      }
    }

    final Integer patternFlags = ReadAction.compute(() -> {
      final PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(regexpFile);
      int flags = 0;
      if (host != null) {
        for (RegExpModifierProvider provider : RegExpModifierProvider.EP.allForLanguage(host.getLanguage())) {
          flags = provider.getFlags(host, regexpFile);
          if (flags > 0) break;
        }
      }
      return flags;
    });

    try {
      //noinspection MagicConstant
      return Pattern.compile(regExp, patternFlags).matcher(StringUtil.newBombedCharSequence(sampleText, 1000)).matches()
             ? RegExpMatchResult.MATCHES
             : RegExpMatchResult.NO_MATCH;
    } catch (ProcessCanceledException pc) {
      return RegExpMatchResult.TIMEOUT;
    }
    catch (Exception ignore) {}

    return RegExpMatchResult.BAD_REGEXP;
  }
}
