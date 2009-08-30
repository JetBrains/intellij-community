/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 15, 2005
 */
public class MuteBreakpointsActionHandler extends DebuggerToggleActionHandler {
  public boolean isSelected(@NotNull final Project project, final AnActionEvent event) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    DebugProcessImpl debugProcess = context.getDebugProcess();
    return debugProcess != null && debugProcess.areBreakpointsMuted();
  }

  public void setSelected(@NotNull final Project project, final AnActionEvent event, final boolean state) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    if(debugProcess != null) {
      debugProcess.setBreakpointsMuted(state);
    }
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    return debugProcess != null; 
  }

}
