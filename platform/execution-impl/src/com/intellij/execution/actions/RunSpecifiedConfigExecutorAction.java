// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RunSpecifiedConfigExecutorAction extends ExecutorAction {
  private static final Logger LOG = Logger.getInstance(RunSpecifiedConfigExecutorAction.class);

  private final RunnerAndConfigurationSettings myRunConfig;
  private final boolean myEditConfigBeforeRun;

  public RunSpecifiedConfigExecutorAction(@NotNull Executor executor,
                                          @NotNull RunnerAndConfigurationSettings runConfig,
                                          boolean editConfigBeforeRun) {
    super(executor);
    myRunConfig = runConfig;
    myEditConfigBeforeRun = editConfigBeforeRun;
  }

  @Override
  protected @NotNull RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
    return myRunConfig;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    if (myEditConfigBeforeRun) {
      presentation.setText(ExecutionBundle.message("choose.run.popup.edit"));
      presentation.setDescription(ExecutionBundle.message("choose.run.popup.edit.description"));
      presentation.setIcon(!ExperimentalUI.isNewUI() ? AllIcons.Actions.EditSource : null);
    }

    // no need in a list of disabled actions in the secondary menu
    // of the 'Current File' item in the combo box drop-down menu
    if (!presentation.isEnabled() &&
        presentation.getClientProperty(WOULD_BE_ENABLED_BUT_STARTING) != Boolean.TRUE) {
      presentation.setVisible(false);
    }
  }

  @Override
  protected void run(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings, @NotNull DataContext dataContext) {
    LOG.assertTrue(myRunConfig == settings);

    if (myEditConfigBeforeRun) {
      String dialogTitle = ExecutionBundle.message("dialog.title.edit.configuration.settings");
      if (!RunDialog.editConfiguration(project, myRunConfig, dialogTitle, myExecutor)) {
        return;
      }
    }

    super.run(project, myRunConfig, dataContext);

    RunManager.getInstance(project).setSelectedConfiguration(myRunConfig);
  }
}
