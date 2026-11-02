// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerLifecycleKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated See {@link ToolWindowViewModeAction} \
 **/
@ApiStatus.Internal
@Deprecated
public class ToggleWindowedModeAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    ToolWindowManagerEx windowManager = ToolWindowManagerLifecycleKt.getToolWindowManagerForEvent(event);
    if (windowManager == null) {
      return false;
    }
    String id = windowManager.getActiveToolWindowId();
    if (id == null) {
      return false;
    }
    return ToolWindowType.WINDOWED == windowManager.getToolWindow(id).getType();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean flag) {
    ToolWindowManagerEx windowManager = ToolWindowManagerLifecycleKt.getToolWindowManagerForEvent(event);
    if (windowManager == null) {
      return;
    }
    String id = windowManager.getActiveToolWindowId();
    if (id == null) {
      return;
    }
    ToolWindowEx toolWindow = (ToolWindowEx)windowManager.getToolWindow(id);
    ToolWindowType type = toolWindow.getType();
    if (ToolWindowType.WINDOWED == type) {
      toolWindow.setType(toolWindow.getInternalType(), null);
    }
    else {
      toolWindow.setType(ToolWindowType.WINDOWED, null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    ToolWindowManagerEx mgr = ToolWindowManagerLifecycleKt.getToolWindowManagerForEvent(event);
    if (mgr == null) {
      presentation.setEnabled(false);
      return;
    }
    String id = mgr.getActiveToolWindowId();
    presentation.setEnabled(id != null && mgr.getToolWindow(id).isAvailable());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
