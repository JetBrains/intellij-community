/*
 * User: anna
 * Date: 20-Feb-2008
 */
package com.intellij.execution.testframework;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.project.Project;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class JavaAwareTestConsoleProperties extends TestConsoleProperties {
  public JavaAwareTestConsoleProperties(final Storage storage, Project project) {
    super(storage, project);
  }

  @Override
  public boolean isDebug() {
    return getDebugSession() != null;
  }

  @Override
  public boolean isPaused() {
    final DebuggerSession debuggerSession = getDebugSession();
    return debuggerSession != null && debuggerSession.isPaused();
  }

  @Nullable
  public DebuggerSession getDebugSession() {
    final DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(getProject());
    if (debuggerManager == null) return null;
    final Collection<DebuggerSession> sessions = debuggerManager.getSessions();
    for (final DebuggerSession debuggerSession : sessions) {
      if (getConsole() == debuggerSession.getProcess().getExecutionResult().getExecutionConsole()) return debuggerSession;
    }
    return null;
  }

}