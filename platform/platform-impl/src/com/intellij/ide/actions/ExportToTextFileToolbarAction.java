// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class ExportToTextFileToolbarAction extends ExportToTextFileAction {
  private final ExporterToTextFile myExporterToTextFile;

  public ExportToTextFileToolbarAction(ExporterToTextFile exporterToTextFile) {
    myExporterToTextFile = exporterToTextFile;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPORT_TO_TEXT_FILE));
  }

  @Override
  protected ExporterToTextFile getExporter(DataContext dataContext) {
    return myExporterToTextFile;
  }
}
