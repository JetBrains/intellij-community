package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.debugger.ui.content.newUI.GridCell;

public class CloseViewAction extends BaseDebuggerViewAction {

  protected void actionPerformed(final GridCell cell, final AnActionEvent e) {
    cell.minimize();
  }
}
