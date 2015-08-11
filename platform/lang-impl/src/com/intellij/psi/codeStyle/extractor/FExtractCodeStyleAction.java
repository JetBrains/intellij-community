/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.extractor.differ.FLangCodeStyleExtractor;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.ui.BalloonLayout;
import com.intellij.psi.codeStyle.extractor.processor.FCodeStyleDeriveProcessor;
import com.intellij.psi.codeStyle.extractor.processor.FGenProcessor;
import com.intellij.psi.codeStyle.extractor.ui.FCodeStyleSettingsNameProvider;
import com.intellij.psi.codeStyle.extractor.values.FValue;
import com.intellij.psi.codeStyle.extractor.values.FValuesContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

public class FExtractCodeStyleAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return;

    Language language = file.getLanguage();

    final FLangCodeStyleExtractor extractor = FLangCodeStyleExtractor.EXTENSION.forLanguage(language);
    if (extractor == null) return;

    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

    final FCodeStyleDeriveProcessor genProcessor = new FGenProcessor(extractor);

    final Task.Backgroundable task = new Task.Backgroundable(project, "Code style extractor", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          CodeStyleSettings cloneSettings = settings.clone();

          FValuesContainer res = genProcessor.runWithProgress(project, cloneSettings, file, indicator);

          reportResult(res, project, cloneSettings, file);
        }
        catch (ProcessCanceledException e) {
          FUtils.logError("Code extraction was canceled");
        }
        catch (Throwable t) {
          t.printStackTrace();
        }
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public void reportResult(final FValuesContainer forSelection, final Project project, final CodeStyleSettings cloneSettings, final PsiFile file) {
    final Balloon balloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(
            "Formatting Options were extracted<br/><a href=\"apply\">Apply</a> <a href=\"details\">Details...</a>",
            MessageType.INFO,
            new HyperlinkListener() {
              @Override
              public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                  boolean apply = "apply".equals(e.getDescription());
                  if (!apply) {
                    @NonNls StringBuilder descriptions = new StringBuilder("<html>Set formatting options:");
                    final java.util.List<FValue> values = forSelection.getValues();
                    final LanguageCodeStyleSettingsProvider[] providers = Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME);
                    Language language = file.getLanguage();
                    FCodeStyleSettingsNameProvider nameProvider = new FCodeStyleSettingsNameProvider(cloneSettings);
                    for (final LanguageCodeStyleSettingsProvider provider : providers) {
                      Language target = provider.getLanguage();
                      if (target.equals(language)) {
                        //this is our language
                        nameProvider.addSettings(descriptions, values, provider);
                        break;
                      }
                    }
                    descriptions.append("</html>");
                    apply = (Messages.YES == Messages.showYesNoCancelDialog(descriptions.toString(),
                            "Extracted Formatted Options",
                            "Apply", "Ignore", "Cancel",
                            Messages.getInformationIcon()));
                    }
                    if (apply) {
                      //create new settings named after the file
                      forSelection.applySelected();
                      CodeStyleScheme derivedScheme = CodeStyleSchemes.getInstance().createNewScheme("Derived from " + file.getName(), null);
                      derivedScheme.getCodeStyleSettings().copyFrom(cloneSettings);
                      CodeStyleSchemes.getInstance().addScheme(derivedScheme);
                      ((CodeStyleSchemesImpl) CodeStyleSchemes.getInstance()).getSchemeManager().setCurrent(derivedScheme);
                      CodeStyleSettingsManager.getInstance(project).PREFERRED_PROJECT_CODE_STYLE = derivedScheme.getName();
                    }
                  }
                }
              }
              ).setDisposable(ApplicationManager.getApplication()).setShowCallout(false).setFadeoutTime(0).
              setShowCallout(false).setAnimationCycle(0).setHideOnClickOutside(false).setHideOnKeyOutside(false).
              setCloseButtonEnabled(true).setHideOnLinkClick(true).createBalloon();

              ApplicationManager.getApplication().

              invokeLater(new Runnable() {
                @Override
                public void run () {
                  Window window = WindowManager.getInstance().getFrame(project);
                  if (window == null) {
                    window = JOptionPane.getRootFrame();
                  }
                  if (window instanceof IdeFrame) {
                    BalloonLayout layout = ((IdeFrame) window).getBalloonLayout();
                    if (layout != null) {
                      layout.add(balloon);
                    }
                  }
                }
              }

              );
            }
  }
