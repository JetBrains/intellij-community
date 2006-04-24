/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
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
  private final RunProfile myProfile;
  private ProcessHandler myProcessHandler;
  private final JavaProgramRunner myRunner;
  private final RunContentDescriptor myDescriptor;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  public RestartAction(final JavaProgramRunner runner,
                       final RunProfile configuration,
                       final ProcessHandler processHandler,
                       final Icon icon,
                       final RunContentDescriptor descritor,
                       RunnerSettings runnerSettings,
                       ConfigurationPerRunnerSettings configurationSettings) {
    super(null, null, icon);
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    getTemplatePresentation().setEnabled(false);
    myProfile = configuration;
    myProcessHandler = processHandler;
    myRunner = runner;
    myDescriptor = descritor;
    // see IDEADEV-698
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    try {
      RunStrategy.getInstance().execute(myProfile, new DataContext() {
        public Object getData(final String dataId) {
          if (RunStrategy.CONTENT_TO_REUSE.equals(dataId)) return myDescriptor;
          return dataContext.getData(dataId);
        }
      }, myRunner, myRunnerSettings, myConfigurationSettings);
    }
    catch(RunCanceledByUserException e1) {
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, e1.getMessage(), ExecutionBundle.message("restart.error.message.title"));
    }
  }

  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setText(ExecutionBundle.message("rerun.configuration.action.name", myProfile.getName()));
    final boolean isRunning = myProcessHandler != null && !myProcessHandler.isProcessTerminated();
    if (myProcessHandler != null && !isRunning) {
      myProcessHandler = null; // already terminated
    }
    presentation.setEnabled(!isRunning && RunStrategy.canExecute(myProfile, myRunner));
  }

  public void registerShortcut(final JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN)), component);
  }
}
