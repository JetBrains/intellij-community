// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HideToolWindowAction extends AnAction implements DumbAware {
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
    toolWindowManager.getToolWindow(id).hide(null);
  }

  static boolean shouldBeHiddenByShortCut(@NotNull ToolWindowManagerEx manager, @Nullable String id) {
    if (id == null) return false;
    ToolWindow window = manager.getToolWindow(id);
    return window.isVisible() && window.getType() != ToolWindowType.WINDOWED && window.getType() != ToolWindowType.FLOATING;
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
    if (id == null) {
      id = toolWindowManager.getLastActiveToolWindowId();
    }
    ToolWindow window = id != null ? toolWindowManager.getToolWindow(id) : null;
    if (window != null &&
        window.isVisible() &&
        UIUtil.isDescendingFrom(IdeFocusManager.getGlobalInstance().getFocusOwner(), window.getComponent())) {
      presentation.setEnabled(true);
    }
    else {
      presentation.setEnabled(shouldBeHiddenByShortCut(toolWindowManager, id));
    }
  }
}