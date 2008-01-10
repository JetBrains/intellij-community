package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

public class StepOutActionHandler extends AbstractSteppingActionHandler {
  public void perform(final Project project, AnActionEvent e) {
    final DebuggerSession session = getSession(project);
    if (session != null) {
      session.stepOut();
    }
  }
}