package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

public class ShowExecutionPointActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession().showExecutionPoint();
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    return debuggerSession != null && debuggerSession.isPaused() &&
           debuggerSession.getContextManager().getContext().getSuspendContext().getThread() != null;
  }
}