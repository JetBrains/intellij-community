// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.util.ExportToFileUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ExportToTextFileAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    ExporterToTextFile exporterToTextFile = getExporter(dataContext);
    if (project == null || exporterToTextFile == null) return;
    if (!exporterToTextFile.canExport()) return;

    export(project, exporterToTextFile);
  }

  public static void export(Project project, ExporterToTextFile exporter) {
    final ExportToFileUtil.ExportDialogBase dlg = new ExportToFileUtil.ExportDialogBase(project, exporter);

    if (!dlg.showAndGet()) {
      return;
    }

    ExportToFileUtil.exportTextToFile(project, dlg.getFileName(), dlg.getText());
    exporter.exportedTo(dlg.getFileName());
  }

  protected ExporterToTextFile getExporter(DataContext dataContext) {
    return PlatformDataKeys.EXPORTER_TO_TEXT_FILE.getData(dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    ExporterToTextFile exporterToTextFile = getExporter(dataContext);
    presentation.setEnabled(
      CommonDataKeys.PROJECT.getData(dataContext) != null && exporterToTextFile != null && exporterToTextFile.canExport());
  }
}
