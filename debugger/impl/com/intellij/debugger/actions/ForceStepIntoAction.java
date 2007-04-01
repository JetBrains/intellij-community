package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ForceStepIntoAction extends AbstractSteppingAction {
  public void actionPerformed(AnActionEvent e) {
    final DebuggerSession session = getSession(e);
    if (session != null) {
      session.stepInto(true, null);
    }
  }
}
