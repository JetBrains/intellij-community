// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ToolSelectComboBox extends BaseToolSelectComboBox<Tool> {
  private final @Nullable Project myProject;

  public ToolSelectComboBox() {
    this(null);
  }

  public ToolSelectComboBox(@Nullable Project project) {
    myProject = project;
  }

  @Override
  protected @NotNull BaseToolManager<Tool> getToolManager() {
    return ToolManager.getInstance();
  }

  @Override
  protected @NotNull ToolSelectDialog getToolSelectDialog(@Nullable String toolIdToSelect) {
    return new ToolSelectDialog(myProject, toolIdToSelect, new ToolsPanel());
  }
}
