package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.debugger.ui.content.newUI.GridCell;

public class CloseViewAction extends BaseDebuggerViewAction {
  void update(final AnActionEvent e, final GridCell cell) {
  }

  public void actionPerformed(final AnActionEvent e) {
    System.out.println("CloseViewAction.actionPerformed");
  }
}
