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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.RegExpModifierProvider;

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
  private Pair<PsiFile, Ref<Balloon>> myParams;

  private EditorTextField mySampleText; //TODO[kb]: make it multiline

  private EditorTextField myRegExp;
  private JPanel myRootPanel;
  private Ref<Balloon> myRef;
  private Project myProject;


  public CheckRegExpForm(Pair<PsiFile, Ref<Balloon>> params) {
    myParams = params;
  }

  private void createUIComponents() {
    PsiFile file = myParams.first;
    myProject = file.getProject();
    myRef = myParams.second;
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);

    myRegExp = new EditorTextField(document, myProject, RegExpLanguage.INSTANCE.getAssociatedFileType());
    final String sampleText = PropertiesComponent.getInstance(myProject).getValue(LAST_EDITED_REGEXP, "Sample Text");
    mySampleText = new EditorTextField(sampleText, myProject, PlainTextFileType.INSTANCE);
    mySampleText.setBorder(
      new CompoundBorder(new EmptyBorder(2, 2, 2, 4), new LineBorder(UIUtil.isUnderDarcula() ? Gray._100 : UIUtil.getBorderColor())));
    mySampleText.setOneLineMode(false);
    mySampleText.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myRootPanel.revalidate();
            final Balloon balloon = myRef.get();
            if (balloon != null) {
              balloon.revalidate();
            }
          }
        });
      }
    });

    myRootPanel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        IdeFocusManager.getGlobalInstance().requestFocus(mySampleText, true);

        new AnAction(){
          @Override
          public void actionPerformed(AnActionEvent e) {
            IdeFocusManager.findInstance().requestFocus(myRegExp.getFocusTarget(), true);
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), mySampleText, myRef.get());
        final AnAction escaper = new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myRef.get().hide();
          }
        };
        escaper.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myRegExp.getFocusTarget(), myRef.get());
        escaper.registerCustomShortcutSet(CommonShortcuts.ESCAPE, mySampleText.getFocusTarget(), myRef.get());


        myRef.get().addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            PropertiesComponent.getInstance(myProject).setValue(LAST_EDITED_REGEXP, mySampleText.getText());
          }
        });

        final Alarm updater = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myRef.get());
        final DocumentAdapter documentListener = new DocumentAdapter() {
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
    };
  }


  public JPanel getRootPanel() {
    return myRootPanel;
  }

  private void updateBalloon() {
    boolean correct = false;
    try {
      final PsiFile file = myParams.first;
      //todo: unfortunately there is no way to access host element representing regexp
      int offset = -1;
      try {
        final String name = file.getName();
        offset = Integer.parseInt(name.substring(name.lastIndexOf(':') + 1, name.lastIndexOf(')')));
      } catch (Exception ignore) {}

      int flags = 0;
      if (offset != -1) {
        final PsiFile host = FileContextUtil.getContextFile(file);
        if (host != null) {
          final PsiElement regexpInHost = host.findElementAt(offset);
          if (regexpInHost != null) {
            for (RegExpModifierProvider provider : RegExpModifierProvider.EP.getExtensions()) {
              final int modifiers = provider.getFlags(regexpInHost, file);
              if (modifiers > 0) {
                flags = modifiers;
                break;
              }
            }
          }
        }
      }
      correct = Pattern.compile(myRegExp.getText(), flags).matcher(mySampleText.getText()).matches();
    } catch (Exception ignore) {}

    mySampleText.setBackground(correct ? new JBColor(new Color(231, 250, 219), new Color(68, 85, 66)) : new JBColor(new Color(255, 177, 160), new Color(110, 43, 40)));
    BalloonImpl balloon = (BalloonImpl)myRef.get();
    if (balloon != null && balloon.isDisposed()) {
      balloon.revalidate();
    }
  }
}
