// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.ide.actions.ExportToTextFileAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.unscramble.ThreadState;

import java.util.List;

public class ExportThreadsAction extends AnAction implements AnAction.TransparentUpdate {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

    final DebuggerSession session = context.getDebuggerSession();
    if(session != null && session.isAttached()) {
      final DebugProcessImpl process = context.getDebugProcess();
      if (process != null) {
        process.getManagerThread().invoke(new DebuggerCommandImpl() {
          protected void action() {
            final List<ThreadState> threads = ThreadDumpAction.buildThreadStates(process.getVirtualMachineProxy());
            ApplicationManager.getApplication().invokeLater(() -> ExportToTextFileAction.export(project, ThreadDumpPanel.createToFileExporter(project, threads)), ModalityState.NON_MODAL);
          }
        });
      }
    }
  }

  public void update(AnActionEvent e){
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
  }
}