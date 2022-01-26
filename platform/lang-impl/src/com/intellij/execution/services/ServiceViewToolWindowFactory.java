// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ServiceViewToolWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).shouldBeAvailable();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).createToolWindowContent(toolWindow);
  }

  @Override
  public void init(@NotNull ToolWindow toolWindow) {
    @Nls String title = UIBundle.message("tool.window.name.services");
    toolWindow.setStripeTitle(title);
  }

}
