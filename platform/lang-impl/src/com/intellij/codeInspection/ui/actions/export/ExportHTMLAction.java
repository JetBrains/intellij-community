// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui.actions.export;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

public class ExportHTMLAction extends ExportActionBase {

  @Override
  public boolean isExportToHTML() {
    return true;
  }

  @Override
  public String getTitle() {
    return InspectionsBundle.message("inspection.generating.html.progress.title");
  }

  @Override
  protected void performExport(@NotNull InspectionResultsView myView, @NotNull String outputDirectoryName) {
    try {
      new InspectionTreeHtmlWriter(myView, outputDirectoryName);
    }
    catch (ProcessCanceledException e) {
      // Do nothing here.
    }
  }

}
