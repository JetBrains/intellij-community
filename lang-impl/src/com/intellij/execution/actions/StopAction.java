package com.intellij.execution.actions;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class StopAction extends AnAction{
  public void actionPerformed(final AnActionEvent e) {
    final RunContentDescriptor contentDescriptor = RunContentManager.RUN_CONTENT_DESCRIPTOR.getData(e.getDataContext());
    final ProcessHandler processHandler = contentDescriptor == null ? null : contentDescriptor.getProcessHandler();
    if(processHandler == null) return;
    if(processHandler.detachIsDefault()) {
      processHandler.detachProcess();
    } else {
      processHandler.destroyProcess();
    }
  }

  public void update(final AnActionEvent e) {
    final RunContentDescriptor contentDescriptor = RunContentManager.RUN_CONTENT_DESCRIPTOR.getData(e.getDataContext());
    final ProcessHandler processHandler = contentDescriptor == null? null : contentDescriptor.getProcessHandler();
    boolean enable = processHandler != null && !processHandler.isProcessTerminating() && !processHandler.isProcessTerminated();
    e.getPresentation().setEnabled(enable);
  }
}
