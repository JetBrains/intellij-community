/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;

/**
 * @author dyoma
 */
public class RestartAction extends AnAction implements DumbAware {
  private ProcessHandler myProcessHandler;
  private final ProgramRunner myRunner;
  private final RunContentDescriptor myDescriptor;
  private final Executor myExecutor;
  private final ExecutionEnvironment myEnvironment;

  public RestartAction(final Executor executor,
                       final ProgramRunner runner,
                       final ProcessHandler processHandler,
                       final Icon icon,
                       final RunContentDescriptor descritor,
                       final ExecutionEnvironment env) {
    super(null, null, icon);
    myEnvironment = env;
    getTemplatePresentation().setEnabled(false);
    myProcessHandler = processHandler;
    myRunner = runner;
    myDescriptor = descritor;
    myExecutor = executor;
    // see IDEADEV-698
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    try {
      final ExecutionEnvironment old = myEnvironment;
      myRunner.execute(myExecutor, new ExecutionEnvironment(old.getRunProfile(), project, old.getRunnerSettings(),
                                                            old.getConfigurationSettings(), myDescriptor));
    }
    catch (RunCanceledByUserException ignore) {
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, e1.getMessage(), ExecutionBundle.message("restart.error.message.title"));
    }
  }

  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setText(ExecutionBundle.message("rerun.configuration.action.name", myEnvironment.getRunProfile().getName()));
    final boolean isRunning = myProcessHandler != null && !myProcessHandler.isProcessTerminated();
    if (myProcessHandler != null && !isRunning) {
      myProcessHandler = null; // already terminated
    }

    presentation.setEnabled(!isRunning /*&& myRunner.canRun(, myProfile)*/);
  }

  public void registerShortcut(final JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN)),
                              component);
  }
}
