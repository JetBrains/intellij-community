// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ExportToHTMLAction extends ExportInspectionResultsActionBase implements DumbAware {

  public ExportToHTMLAction() {
    super("HTML", "Exports inspection results to HTML", null, true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    super.actionPerformed(e);
    final InspectionResultsView view = getView(e);

    if (view == null) {
      return;
    }
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(view.getProject());
    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;

    ApplicationManager.getApplication().invokeLater(() -> {
      final Runnable exportRunnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
        try {
          new InspectionTreeHtmlWriter(view, outputDirectoryName);
        }
        catch (ProcessCanceledException ex) {
          // Do nothing here.
        }
      });

      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
        exportRunnable, InspectionsBundle.message("inspection.generating.html.progress.title"), true, view.getProject())) {
        return;
      }

      if (exportToHTMLSettings.OPEN_IN_BROWSER) {
        BrowserUtil.browse(new File(exportToHTMLSettings.OUTPUT_DIRECTORY, "index.html"));
      }
    });
  }

}
