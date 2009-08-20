
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.actions.ChooseDebugConfigurationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

public class ResumeActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerSession session = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    if (session != null && session.isPaused()) {
      session.resume();
    } else {
      new ChooseDebugConfigurationAction().actionPerformed(event);
    }
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    return debuggerSession == null || debuggerSession.isPaused();
  }
}
