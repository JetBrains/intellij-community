package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;

public class TogglePinnedModeAction extends ToggleAction {
  public boolean isSelected(AnActionEvent event){
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      return false;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return !mgr.getToolWindow(id).isAutoHide();
  }

  public void setSelected(AnActionEvent event,boolean flag) {
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return;
    }
    mgr.getToolWindow(id).setAutoHide(!flag);
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
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
    presentation.setEnabled(toolWindow.isAvailable()&&ToolWindowType.SLIDING!=toolWindow.getType());
  }
}
