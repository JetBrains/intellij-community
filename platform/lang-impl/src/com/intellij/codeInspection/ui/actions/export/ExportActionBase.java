// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui.actions.export;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 *  Every code inspection export action has to inherit this class. Register your implementation with "CodeInspectionExport" id prefix.
 *  i.e.<action id="CodeInspectionExport.XLS" text="XLS" class="..." />
 */
public abstract class ExportActionBase extends AnAction implements DumbAware {

  public abstract boolean isExportToHTML();

  protected abstract String getTitle();

  protected abstract void performExport(@NotNull InspectionResultsView myView, @NotNull String outputDirectoryName);

  @Override
  public void actionPerformed(AnActionEvent e) {}

  public void actionPerformed(final InspectionResultsView myView,
                              final String outputDirectoryName,
                              ExportToHTMLSettings exportToHTMLSettings) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final Runnable exportRunnable = () -> ApplicationManager.getApplication().runReadAction(() -> performExport(myView, outputDirectoryName));

      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable,
                                                                             getTitle(), true,
                                                                             myView.getProject())) {
        return;
      }

      if (isExportToHTML() && exportToHTMLSettings.OPEN_IN_BROWSER) {
        BrowserUtil.browse(new File(exportToHTMLSettings.OUTPUT_DIRECTORY, "index.html"));
      }
    });
  }

  public boolean isVisible(@NotNull InspectionResultsView myView) {
    return true;
  }
}
