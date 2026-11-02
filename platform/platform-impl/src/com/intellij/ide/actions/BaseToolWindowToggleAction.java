// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerLifecycleKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class BaseToolWindowToggleAction extends ToggleAction implements ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  @Override
  public final boolean isSelected(@NotNull AnActionEvent e) {
    ToolWindowManagerEx mgr = ToolWindowManagerLifecycleKt.getToolWindowManagerForEvent(e);
    if (mgr == null) {
      return false;
    }
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return isSelected(mgr.getToolWindow(id));
  }

  protected abstract boolean isSelected(ToolWindow window);

  @Override
  public final void setSelected(@NotNull AnActionEvent e, boolean state) {
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerLifecycleKt.getToolWindowManagerForEvent(e);
    if (toolWindowManager == null) {
      return;
    }
    String id = toolWindowManager.getActiveToolWindowId();
    if (id == null) {
      return;
    }

    setSelected(toolWindowManager.getToolWindow(id), state);
  }

  protected abstract void setSelected(ToolWindow window, boolean state);

  @Override
  public final void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    ToolWindowManagerEx mgr = ToolWindowManagerLifecycleKt.getToolWindowManagerForEvent(e);
    if (mgr == null) {
      presentation.setEnabled(false);
      return;
    }
    String id=mgr.getActiveToolWindowId();

    if (id == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindow window = mgr.getToolWindow(id);

    if (window == null) {
      presentation.setEnabled(false);
      return;
    }

    update(window, presentation);
  }

  protected abstract void update(ToolWindow window, Presentation presentation);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
