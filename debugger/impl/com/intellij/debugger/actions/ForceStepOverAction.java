package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ForceStepOverAction extends AbstractSteppingAction {
  public void actionPerformed(AnActionEvent e) {
    final DebuggerSession session = getSession(e);
    if (session != null) {
      session.stepOver(true);
    }
  }
}

