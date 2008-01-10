package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ForceStepOverActionHandler extends AbstractSteppingActionHandler {
  public void perform(final Project project, AnActionEvent e) {
    final DebuggerSession session = getSession(project);
    if (session != null) {
      session.stepOver(true);
    }
  }
}

