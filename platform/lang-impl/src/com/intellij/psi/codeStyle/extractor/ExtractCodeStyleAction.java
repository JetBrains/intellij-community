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

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.processor.CodeStyleDeriveProcessor;
import com.intellij.psi.codeStyle.extractor.processor.GenProcessor;
import com.intellij.psi.codeStyle.extractor.ui.ExtractPreviewDialog;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.Map;

import static com.intellij.psi.codeStyle.extractor.Utils.updateState;

public class ExtractCodeStyleAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    PsiFile file = null;
    if (editor == null && files != null && files.length == 1 && !files[0].isDirectory()) {
      file = PsiManager.getInstance(project).findFile(files[0]);
    }
    else if (editor != null) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    if (file == null) {
      return;
    }

    Language language = file.getLanguage();
    final LangCodeStyleExtractor extractor = LangCodeStyleExtractor.EXTENSION.forLanguage(language);
    if (extractor == null) {
      return;
    }

    final CodeStyleSettings settings = CodeStyle.getSettings(file);

    final CodeStyleDeriveProcessor genProcessor = new GenProcessor(extractor);
    final PsiFile finalFile = file;
    
    final Task.Backgroundable task = new Task.Backgroundable(project, "Code Style Extractor", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          updateState(indicator, getTitle(), true);
          indicator.setIndeterminate(false);
          
          CodeStyleSettings cloneSettings = settings.clone();
          Map<Value, Object> backup = genProcessor.backupValues(cloneSettings, language);
          ValuesExtractionResult res = genProcessor.runWithProgress(project, cloneSettings, finalFile, indicator);
          reportResult(genProcessor.getHTMLReport(), res, project, cloneSettings, finalFile, backup);
        }
        catch (ProcessCanceledException e) {
          Utils.logError("Code extraction was canceled");
        }
        catch (Throwable t) {
          Utils.logError("Unexpected exception: " + t);
        }
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public void reportResult(@NotNull final String htmlReport, 
                           @NotNull final ValuesExtractionResult calculatedValues,
                           @NotNull final Project project,
                           @NotNull final CodeStyleSettings cloneSettings,
                           @NotNull final PsiFile file,
                           @NotNull final Map<Value, Object> backup) {
    UIUtil.invokeLaterIfNeeded(() -> {
      final Balloon balloon = JBPopupFactory
        .getInstance()
        .createHtmlTextBalloonBuilder(
          "<html>Code style was extracted from <b>" + file.getName() + "</b><br>"
          + htmlReport
          + "<br><a href=\"apply\">Apply...</a>",
          MessageType.INFO,
          new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
              if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
                  && "apply".equals(e.getDescription())
                  && new ExtractPreviewDialog(file, cloneSettings, calculatedValues, htmlReport).showAndGet()) {
                calculatedValues.applySelected();
                CodeStyleScheme derivedScheme = CodeStyleSchemes
                  .getInstance()
                  .createNewScheme("Derived from " + file.getName(), null);
                derivedScheme.getCodeStyleSettings().copyFrom(cloneSettings);
                CodeStyleSchemes.getInstance().addScheme(derivedScheme);
                CodeStyleSchemesImpl.getSchemeManager().setCurrent(derivedScheme);
                CodeStyleSettingsManager.getInstance(project).PREFERRED_PROJECT_CODE_STYLE = derivedScheme.getName();
              }
            }
          }
        )
        .setFadeoutTime(0)
        .setShowCallout(false)
        .setAnimationCycle(0)
        .setHideOnClickOutside(false)
        .setHideOnKeyOutside(false)
        .setHideOnLinkClick(true)
        .setCloseButtonEnabled(true)
        .createBalloon();

      Disposer.register(project, balloon);

      Window window = WindowManager.getInstance().getFrame(project);
      if (window == null) {
        window = JOptionPane.getRootFrame();
      }
      if (window instanceof IdeFrame) {
        BalloonLayout layout = ((IdeFrame)window).getBalloonLayout();
        if (layout != null) {
          balloon.show(new PositionTracker<Balloon>(((IdeFrame)window).getComponent()) {
            @Override
            public RelativePoint recalculateLocation(Balloon object) {
              Component c = getComponent();
              int y = c.getHeight() - 45;
              return new RelativePoint(c, new Point(c.getWidth() - 150, y));
            }
          }, Balloon.Position.above);
        }
      }
    });
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    PsiFile file = null;
    if (editor != null) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }
    else {
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
