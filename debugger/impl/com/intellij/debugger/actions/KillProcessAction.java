/**
 * class ExportThreadsAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

public class KillProcessAction extends AnAction {
  public KillProcessAction() {
    super(DebuggerBundle.message("action.kill.process.text"), DebuggerBundle.message("action.kill.process.description"), IconLoader.getIcon("/debugger/killProcess.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl context = DebuggerAction.getDebuggerContext(e.getDataContext());
    if (DebuggerContextImpl.EMPTY_CONTEXT.equals(context)) {
      return;
    }

    final DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      session.getProcess().stop(true);
    }
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    final DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    if (DebuggerContextImpl.EMPTY_CONTEXT.equals(context)) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = context.getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isAttached());
  }
}