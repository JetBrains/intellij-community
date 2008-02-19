package com.intellij.debugger.actions;

import com.intellij.execution.ui.layout.NewDebuggerContentUI;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

public class ToggleToolbarLayout extends ToggleAction {

  public void update(final AnActionEvent e) {
    if (getRunnerUi(e) == null) {
      e.getPresentation().setEnabled(false);
    } else {
      super.update(e);
    }
  }

  public boolean isSelected(final AnActionEvent e) {
    final NewDebuggerContentUI ui = getRunnerUi(e);
    return ui != null ? ui.isHorizontalToolbar() : false;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    getRunnerUi(e).setHorizontalToolbar(state);
  }

  private static NewDebuggerContentUI getRunnerUi(final AnActionEvent e) {
    return NewDebuggerContentUI.KEY.getData(e.getDataContext());
  }

}
