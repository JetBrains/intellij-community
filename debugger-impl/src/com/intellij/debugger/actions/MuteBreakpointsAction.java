/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 15, 2005
 */
public class MuteBreakpointsAction extends ToggleAction{

  public boolean isSelected(AnActionEvent e) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(e.getDataContext());
    DebugProcessImpl debugProcess = context.getDebugProcess();
    if(debugProcess != null) {
      return debugProcess.areBreakpointsMuted();
    }
    return false;
  }

  public void setSelected(AnActionEvent e, boolean state) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    if(debugProcess != null) {
      debugProcess.setBreakpointsMuted(state);
    }
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    e.getPresentation().setEnabled(debugProcess != null);
  }

}
