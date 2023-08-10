// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

final class ShowServicesAction extends ToggleAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    Content content = toolWindow != null ? toolWindow.getContentManager().getSelectedContent() : null;
    e.getPresentation().setEnabledAndVisible(content != null &&
                                             content.getComponent() instanceof ServiceView);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return true;

    return ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).isShowServicesTree();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;

    ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).setShowServicesTree(state);
  }
}

