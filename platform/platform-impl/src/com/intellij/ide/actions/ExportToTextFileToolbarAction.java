// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ExportToTextFileToolbarAction extends ExportToTextFileAction {
  private final ExporterToTextFile myExporterToTextFile;

  public ExportToTextFileToolbarAction(ExporterToTextFile exporterToTextFile) {
    myExporterToTextFile = exporterToTextFile;
    ActionUtil.copyFrom(this, IdeActions.ACTION_EXPORT_TO_TEXT_FILE);
  }

  @Override
  protected ExporterToTextFile getExporter(DataContext dataContext) {
    return myExporterToTextFile;
  }
}
