// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ToolBeforeRunTaskProvider extends AbstractToolBeforeRunTaskProvider<ToolBeforeRunTask> implements DumbAware {
  static final Key<ToolBeforeRunTask> ID = Key.create("ToolBeforeRunTask");

  @Override
  public Key<ToolBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return ToolsBundle.message("tools.before.run.provider.name");
  }

  @Override
  public ToolBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new ToolBeforeRunTask();
  }

  @Override
  protected ToolsPanel createToolsPanel() {
    return new ToolsPanel();
  }
}
