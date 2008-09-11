package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class ToggleSideModeAction extends ToggleAction{
  public boolean isSelected(AnActionEvent event){
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      return false;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id=windowManager.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return windowManager.getToolWindow(id).isSideTool();
  }

  public void setSelected(AnActionEvent event,boolean flag){
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      return;
    }
    String id=ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if(id==null){
      return;
    }

    ToolWindowManagerEx mgr=ToolWindowManagerEx.getInstanceEx(project);
    ToolWindowEx toolWindow=(ToolWindowEx)mgr.getToolWindow(id);
    toolWindow.setSideTool(flag, null);
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    presentation.setEnabled(id!=null&&mgr.getToolWindow(id).isAvailable());
  }
}
