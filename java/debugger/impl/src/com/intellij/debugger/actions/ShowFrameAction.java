package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 7:45:21 PM
 */
public class ShowFrameAction extends GotoFrameSourceAction {
  public void actionPerformed(AnActionEvent e) {
    super.actionPerformed(e);
    DebuggerStateManager stateManager = getContextManager(e.getDataContext());
    DebuggerContextImpl context = stateManager.getContext();

    if(context != null) {
      DebuggerPanelsManager.getInstance(context.getProject()).showFramePanel();
    }
  }
}
