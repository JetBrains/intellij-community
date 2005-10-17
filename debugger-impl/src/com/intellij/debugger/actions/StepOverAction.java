
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

public class StepOverAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession().stepOver(false);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = context.getDebuggerSession();

    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();

    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    presentation.setEnabled(isPaused && hasCurrentThread);
  }
}
