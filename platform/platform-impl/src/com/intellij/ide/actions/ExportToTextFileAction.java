/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.util.ExportToFileUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class ExportToTextFileAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    ExporterToTextFile exporterToTextFile = getExporter(dataContext);
    if (project == null || exporterToTextFile == null) return;
    if (!exporterToTextFile.canExport()) return;

    export(project, exporterToTextFile);
  }

  public static void export(Project project, ExporterToTextFile exporter) {
    final ExportToFileUtil.ExportDialogBase dlg = new ExportToFileUtil.ExportDialogBase(project, exporter);

    dlg.show();
    if (!dlg.isOK()) {
      return;
    }

    ExportToFileUtil.exportTextToFile(project, dlg.getFileName(), dlg.getText());
    exporter.exportedTo(dlg.getFileName());
  }

  protected ExporterToTextFile getExporter(DataContext dataContext) {
    return PlatformDataKeys.EXPORTER_TO_TEXT_FILE.getData(dataContext);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    ExporterToTextFile exporterToTextFile = getExporter(dataContext);
    presentation.setEnabled(PlatformDataKeys.PROJECT.getData(dataContext) != null && exporterToTextFile != null && exporterToTextFile.canExport());
  }
}
