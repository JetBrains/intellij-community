// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ToolSelectDialog extends DialogWrapper {
  private final BaseToolsPanel myToolsPanel;

  public ToolSelectDialog(@Nullable Project project, @Nullable String actionIdToSelect, BaseToolsPanel toolsPanel) {
    super(project);
    myToolsPanel = toolsPanel;
    myToolsPanel.reset();
    init();
    pack();
    if (actionIdToSelect != null) {
      myToolsPanel.selectTool(actionIdToSelect);
    }
    setTitle(ToolsBundle.message("tools.dialog.title"));
  }

  @Override
  protected void doOKAction() {
    myToolsPanel.apply();
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myToolsPanel;
  }

  public @Nullable Tool getSelectedTool() {
    return myToolsPanel.getSingleSelectedTool();
  }

  public boolean isModified() {
    return myToolsPanel.isModified();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.tools.ToolSelectDialog.dimensionServiceKey";
  }
}
