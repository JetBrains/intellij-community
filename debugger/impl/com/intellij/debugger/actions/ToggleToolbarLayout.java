package com.intellij.debugger.actions;

import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;

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
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    if(project == null) return null;
    return DebuggerPanelsManager.getInstance(project).getSessionTab();
  }

}
