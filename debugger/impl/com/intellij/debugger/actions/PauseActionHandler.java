package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

public class PauseActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession().pause();
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    return debuggerSession != null && !debuggerSession.getProcess().isPausePressed() &&
           (debuggerSession.isEvaluating() || debuggerSession.isRunning());
  }
}
