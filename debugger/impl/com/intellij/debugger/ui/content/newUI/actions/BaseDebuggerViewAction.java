package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.GridCell;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

abstract class BaseDebuggerViewAction extends AnAction {

  public void update(final AnActionEvent e) {
    GridCell cell = e.getData(GridCell.KEY);
    if (cell != null) {
      update(e, cell);
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  abstract void update(AnActionEvent e, GridCell cell);

}
