package com.intellij.execution.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class StopAction extends AnAction{
  public void actionPerformed(final AnActionEvent e) {
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());

    if(project == null) return;

    final RunContentDescriptor selectedContent = ExecutionManager.getInstance(project).getContentManager().getSelectedContent();

    if(selectedContent == null) return;

    final ProcessHandler selectedProcessHandler = selectedContent.getProcessHandler();

    if(selectedProcessHandler == null) return;

    if(selectedProcessHandler.detachIsDefault()) {
      selectedProcessHandler.detachProcess();
    } else {
      selectedProcessHandler.destroyProcess();
    }
  }

  public void update(final AnActionEvent e) {
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());

    boolean enable = false;

    if(project != null) {
      final RunContentDescriptor selectedContent = ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
      if (selectedContent != null) {
        final ProcessHandler selectedProcessHandler = selectedContent.getProcessHandler();

        if(selectedProcessHandler != null) {
          enable = !selectedProcessHandler.isProcessTerminating() && !selectedProcessHandler.isProcessTerminated();
        }
      }
    }

    e.getPresentation().setEnabled(enable);
  }
}
