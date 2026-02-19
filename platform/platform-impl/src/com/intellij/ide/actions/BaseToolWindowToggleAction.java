// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class BaseToolWindowToggleAction extends ToggleAction implements ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  @Override
  public final boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return false;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return isSelected(mgr.getToolWindow(id));
  }

  protected abstract boolean isSelected(ToolWindow window);

  @Override
  public final void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
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
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
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
