// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;

public class HideSideWindowsAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id == null) {
      id = toolWindowManager.getLastActiveToolWindowId();
    }
    if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)) {
      toolWindowManager.hideToolWindow(id, true);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id != null) {
      presentation.setEnabled(true);
      return;
    }

    id = toolWindowManager.getLastActiveToolWindowId();
    if (id == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id));
  }
}
