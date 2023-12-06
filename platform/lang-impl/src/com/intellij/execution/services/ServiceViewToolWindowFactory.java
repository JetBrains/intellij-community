// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

// cannot be final - used externally
public class ServiceViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).shouldBeAvailable();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).createToolWindowContent(toolWindow);
  }
}
