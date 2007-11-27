package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.GridCell;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class MoveToGridAction extends BaseDebuggerViewAction {
  protected void update(final AnActionEvent e, final GridCell cell) {
    e.getPresentation().setVisible(false);
  }

  protected void actionPerformed(final GridCell cell, final AnActionEvent e) {
  }
}
