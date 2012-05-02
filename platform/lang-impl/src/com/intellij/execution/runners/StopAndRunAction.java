/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

/**
 * @author dyoma
 */
public class StopAndRunAction extends RestartAction{

  public StopAndRunAction(Executor executor,
                          ProgramRunner runner,
                          ProcessHandler processHandler,
                          Icon icon,
                          RunContentDescriptor descritor, ExecutionEnvironment env) {
    super(executor, runner, processHandler, icon, descritor, env);
  }

  public void actionPerformed(final AnActionEvent e) {
    ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM).actionPerformed(e);
    super.actionPerformed(e);
  }


  public void update(final AnActionEvent event) {
    super.update(event);
    final ProcessHandler handler = getProcessHandler();
    final boolean isRunning = handler != null && !handler.isProcessTerminated();
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(isRunning);
  }
}
