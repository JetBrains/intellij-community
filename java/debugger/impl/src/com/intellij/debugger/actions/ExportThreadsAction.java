/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * class ExportThreadsAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.ide.actions.ExportToTextFileAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.unscramble.ThreadState;

import java.util.List;

public class ExportThreadsAction extends AnAction implements AnAction.TransparentUpdate {
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

    final DebuggerSession session = context.getDebuggerSession();
    if(session != null && session.isAttached()) {
      final DebugProcessImpl process = context.getDebugProcess();
      if (process != null) {
        process.getManagerThread().invoke(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            final List<ThreadState> threads = ThreadDumpAction.buildThreadStates(process.getVirtualMachineProxy());
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                ExportToTextFileAction.export(project, ThreadDumpPanel.createToFileExporter(project, threads));
              }
            }, ModalityState.NON_MODAL);
          }
        });
      }
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
  }
}