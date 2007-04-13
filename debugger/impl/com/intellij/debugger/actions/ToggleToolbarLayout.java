package com.intellij.debugger.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.debugger.ui.DebuggerPanelsManager;

public class ToggleToolbarLayout extends ToggleAction {

  public void update(final AnActionEvent e) {
    if (getSessionTab(e) == null) {
      e.getPresentation().setEnabled(false);
    } else {
      super.update(e);
    }
  }

  public boolean isSelected(final AnActionEvent e) {
    return getSessionTab(e).getContentUi().isHorizontalToolbar();
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    getSessionTab(e).getContentUi().setHorizontalToolbar(state);
  }

  private DebuggerSessionTab getSessionTab(final AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    if(project == null) return null;
    return DebuggerPanelsManager.getInstance(project).getSessionTab();
  }

}
