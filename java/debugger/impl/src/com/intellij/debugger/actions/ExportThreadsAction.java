// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.ide.util.ExportToFileUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.threadDumpParser.ThreadState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExportThreadsAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

    final DebuggerSession session = context.getDebuggerSession();
    if (session != null && session.isAttached()) {
      final DebugProcessImpl process = context.getDebugProcess();
      DebuggerManagerThreadImpl managerThread = context.getManagerThread();
      if (process != null && managerThread != null) {
        managerThread.invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            final List<ThreadState> threads = ThreadDumpAction.buildThreadStates(process.getVirtualMachineProxy());
            ApplicationManager.getApplication().invokeLater(() -> ExportToFileUtil.chooseFileAndExport(project, ThreadDumpPanel.createToFileExporter(project, threads)), ModalityState.nonModal());
          }
        });
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}