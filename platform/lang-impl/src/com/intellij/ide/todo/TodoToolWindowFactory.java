// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;


public final class TodoToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(final @NotNull Project project, final @NotNull ToolWindow toolWindow) {
    project.getService(TodoView.class).initToolWindow(toolWindow);
  }
}
