// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated See {@link ToolWindowViewModeAction} \
 **/
@Deprecated
public class ToggleDockModeAction extends ToggleAction implements DumbAware {

  @Override
  public boolean isSelected(@NotNull AnActionEvent event){
    Project project = event.getProject();
    if (project == null) {
      return false;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id=windowManager.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return ToolWindowType.DOCKED==windowManager.getToolWindow(id).getType();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean flag){
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id=windowManager.getActiveToolWindowId();
    if(id==null){
      return;
    }
    ToolWindow toolWindow=windowManager.getToolWindow(id);
    ToolWindowType type=toolWindow.getType();
    if(ToolWindowType.DOCKED==type){
      toolWindow.setType(ToolWindowType.SLIDING, null);
    } else if(ToolWindowType.SLIDING==type){
      toolWindow.setType(ToolWindowType.DOCKED, null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      presentation.setEnabled(false);
      return;
    }
    ToolWindow toolWindow=mgr.getToolWindow(id);
    presentation.setEnabled(toolWindow.isAvailable()
      && toolWindow.getType() != ToolWindowType.FLOATING
      && toolWindow.getType() != ToolWindowType.WINDOWED);
  }
}
