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
package com.intellij.psi.codeStyle.extractor;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.processor.CodeStyleDeriveProcessor;
import com.intellij.psi.codeStyle.extractor.processor.GenProcessor;
import com.intellij.psi.codeStyle.extractor.ui.CodeStyleSettingsNameProvider;
import com.intellij.psi.codeStyle.extractor.ui.ExtractedSettingsDialog;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.ui.BalloonLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class ExtractCodeStyleAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    PsiFile file = null;
    if (editor == null && files != null && files.length == 1 && !files[0].isDirectory()) {
      file = PsiManager.getInstance(project).findFile(files[0]);
    } else if (editor != null) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    if (file == null) return;

    Language language = file.getLanguage();

    final LangCodeStyleExtractor extractor = LangCodeStyleExtractor.EXTENSION.forLanguage(language);
    if (extractor == null) return;

    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

    final CodeStyleDeriveProcessor genProcessor = new GenProcessor(extractor);

    final PsiFile finalFile = file;
    final Task.Backgroundable task = new Task.Backgroundable(project, "Code style extractor", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          CodeStyleSettings cloneSettings = settings.clone();

          Map<Value, Object> backup = genProcessor.backupValues(cloneSettings, finalFile.getLanguage());

          ValuesExtractionResult res = genProcessor.runWithProgress(project, cloneSettings, finalFile, indicator);

          reportResult(res, project, cloneSettings, finalFile, backup);
        }
        catch (ProcessCanceledException e) {
          Utils.logError("Code extraction was canceled");
        }
        catch (Throwable t) {
          Utils.logError("Unexpected exception:\n" + t);
        }
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public void reportResult(final ValuesExtractionResult forSelection, final Project project,
                           final CodeStyleSettings cloneSettings, final PsiFile file, final Map<Value, Object> backup) {
    final Balloon balloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(
            "Formatting Options were extracted<br/><a href=\"apply\">Apply</a> <a href=\"details\">Details...</a>",
            MessageType.INFO,
            new HyperlinkListener() {
              @Override
              public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                  boolean apply = "apply".equals(e.getDescription());
                  ExtractedSettingsDialog myDialog = null;
                  if (!apply) {
                    final List<Value> values = forSelection.getValues();
                    final LanguageCodeStyleSettingsProvider[] providers = Extensions.getExtensions(
                      LanguageCodeStyleSettingsProvider.EP_NAME);
                    Language language = file.getLanguage();
                    CodeStyleSettingsNameProvider nameProvider = new CodeStyleSettingsNameProvider();
                    for (final LanguageCodeStyleSettingsProvider provider : providers) {
                      Language target = provider.getLanguage();
                      if (target.equals(language)) {
                        //this is our language
                        nameProvider.addSettings(provider);
                        myDialog = new ExtractedSettingsDialog(project, nameProvider, values);
                        apply = myDialog.showAndGet();
                        break;
                      }
                    }
                    }
                    if (apply && myDialog != null) {
                      //create new settings named after the file
                      final ExtractedSettingsDialog finalMyDialog = myDialog;
                      forSelection.applyConditioned(new Condition<Value>() {
                        @Override
                        public boolean value(Value value) {
                          return finalMyDialog.valueIsSelectedInTree(value);
                        }
                      }, backup);
                      CodeStyleScheme derivedScheme = CodeStyleSchemes.getInstance().createNewScheme("Derived from " + file.getName(), null);
                      derivedScheme.getCodeStyleSettings().copyFrom(cloneSettings);
                      CodeStyleSchemes.getInstance().addScheme(derivedScheme);
                      CodeStyleSchemesImpl.getSchemeManager().setCurrent(derivedScheme);
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

    @Override
    public void update(AnActionEvent event){
      Presentation presentation = event.getPresentation();
      DataContext dataContext = event.getDataContext();
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null){
        presentation.setEnabled(false);
        return;
      }

      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

      PsiFile file = null;
      if (editor != null){
        file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      } else {
        final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
        if (files != null && files.length == 1 && !files[0].isDirectory()) {
          file = PsiManager.getInstance(project).findFile(files[0]);
        }
      }

      if (file == null || file.getVirtualFile() == null) {
        presentation.setEnabled(false);
        return;
      }

      if (LanguageFormatting.INSTANCE.forContext(file) != null) {
        presentation.setEnabled(true);
      }
    }
  }
