package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.GridCell;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

abstract class BaseDebuggerViewAction extends AnAction {

  public final void update(final AnActionEvent e) {
    GridCell cell = getCell(e);
    if (cell != null) {
      update(e, cell);
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  private GridCell getCell(final AnActionEvent e) {
    GridCell cell = e.getData(GridCell.KEY);
    return cell;
  }

  protected void update(AnActionEvent e, GridCell cell) {

  }

  public final void actionPerformed(final AnActionEvent e) {
    actionPerformed(getCell(e), e);    
  }

  protected abstract void actionPerformed(GridCell cell, AnActionEvent e);
}
