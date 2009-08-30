package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class StepIntoActionHandler extends AbstractSteppingActionHandler {
  public void perform(@NotNull final Project project, AnActionEvent e) {
    final DebuggerSession session = getSession(project);
    if (session != null) {
      session.stepInto(false, null);
    }
  }
}
