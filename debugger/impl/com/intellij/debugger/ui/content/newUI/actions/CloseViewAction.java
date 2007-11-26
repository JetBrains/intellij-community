package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.GridCell;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class CloseViewAction extends BaseDebuggerViewAction {

  protected void update(final AnActionEvent e, final GridCell cell) {
    super.update(e, cell);
    if (GridCell.POPUP_PLACE.equals(e.getPlace())) {
      e.getPresentation().setIcon(null);
    }
  }

  protected void actionPerformed(final GridCell cell, final AnActionEvent e) {
    cell.minimize();
  }
}
