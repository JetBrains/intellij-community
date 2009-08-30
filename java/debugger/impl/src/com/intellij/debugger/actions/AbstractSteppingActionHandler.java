package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class AbstractSteppingActionHandler extends DebuggerActionHandler {

  @Nullable
  protected static DebuggerSession getSession(@NotNull Project project) {
    final DebuggerContextImpl context = getContext(project);
    return context != null ? context.getDebuggerSession() : null;
  }

  @Nullable
  private static DebuggerContextImpl getContext(@NotNull Project project) {
    return (DebuggerManagerEx.getInstanceEx(project)).getContext();
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl context = getContext(project);
    if (context == null) {
      return false;
    }

    DebuggerSession debuggerSession = context.getDebuggerSession();

    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();
    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    return isPaused && hasCurrentThread;
  }

}
