/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;

/**
 * @author dyoma
 */
public class RestartAction extends AnAction {
  private ProcessHandler myProcessHandler;
  private final ProgramRunner myRunner;
  private final RunContentDescriptor myDescriptor;
  private Executor myExecutor;
  private ExecutionEnvironment myEnvironment;

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
      myRunner.execute(myExecutor, new ExecutionEnvironment(old.getRunProfile(), old.getRunnerSettings(), old.getConfigurationSettings(), new DataContext() {
        public Object getData(final String dataId) {
          if (GenericProgramRunner.CONTENT_TO_REUSE.equals(dataId)) return myDescriptor;
          return dataContext.getData(dataId);
        }
      }));
    }
    catch (RunCanceledByUserException e1) {
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
