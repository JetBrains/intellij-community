// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import org.jetbrains.annotations.NotNull;

public final class JumpToLastWindowAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    ToolWindowManagerImpl manager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
    String id = manager.getLastActiveToolWindowId();
    if (id == null || !manager.getToolWindow(id).isAvailable()) {
      return;
    }
    manager.activateToolWindow(id, null, true, ToolWindowEventSource.JumpToLastWindowAction);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    String id = manager.getLastActiveToolWindowId();
    presentation.setEnabled(id != null && manager.getToolWindow(id).isAvailable());
  }
}
