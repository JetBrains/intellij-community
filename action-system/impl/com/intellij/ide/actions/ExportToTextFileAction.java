package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.util.ExportToFileUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class ExportToTextFileAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    ExporterToTextFile exporterToTextFile = getExporter(dataContext);
    if (project == null || exporterToTextFile == null) return;
    if (!exporterToTextFile.canExport()) return;

    export(project, exporterToTextFile);
  }

  public void export(Project project, ExporterToTextFile exporter) {
    final ExportToFileUtil.ExportDialogBase dlg = new ExportToFileUtil.ExportDialogBase(project, exporter);

    dlg.show();
    if (!dlg.isOK()) {
      return;
    }

    ExportToFileUtil.exportTextToFile(project, dlg.getFileName(), dlg.getText());
    exporter.exportedTo(dlg.getFileName());
  }

  protected ExporterToTextFile getExporter(DataContext dataContext) {
    ExporterToTextFile exporterToTextFile = (ExporterToTextFile)dataContext.getData(DataConstants.EXPORTER_TO_TEXT_FILE);
    return exporterToTextFile;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    ExporterToTextFile exporterToTextFile = getExporter(dataContext);
    presentation.setEnabled(dataContext.getData(DataConstants.PROJECT) != null && exporterToTextFile != null && exporterToTextFile.canExport());
  }
}
