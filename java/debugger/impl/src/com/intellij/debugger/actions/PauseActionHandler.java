package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

public class PauseActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession().pause();
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    return debuggerSession != null && !debuggerSession.getProcess().isPausePressed() &&
           (debuggerSession.isEvaluating() || 
            debuggerSession.isRunning() || isSingleThreadSuspended(debuggerSession)
           );
  }

  private static boolean isSingleThreadSuspended(final DebuggerSession debuggerSession) {
    final SuspendContextImpl suspendContext = debuggerSession.getContextManager().getContext().getSuspendContext();
    return suspendContext != null && !suspendContext.isResumed() && suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD;
  }
}
