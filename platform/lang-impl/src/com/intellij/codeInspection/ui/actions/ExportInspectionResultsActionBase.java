// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public abstract class ExportInspectionResultsActionBase extends InspectionViewActionBase {

  private final boolean canBeOpenInBrowser;

  public ExportInspectionResultsActionBase(
    @Nullable String text,
    @Nullable String description,
    @Nullable Icon icon,
    boolean canBeOpenInBrowser
  ) {
    super(text, description, icon);
    this.canBeOpenInBrowser = canBeOpenInBrowser;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final InspectionResultsView view = getView(e);

    if (view == null) {
      return;
    }
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(view.getProject(), canBeOpenInBrowser);
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(view.getProject());

    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "inspections";
    }
    exportToHTMLDialog.reset();

    if (!exportToHTMLDialog.showAndGet()) {
      return;
    }
    exportToHTMLDialog.apply();
  }
}
