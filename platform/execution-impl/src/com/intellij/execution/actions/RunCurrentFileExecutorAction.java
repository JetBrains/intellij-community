// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class RunCurrentFileExecutorAction extends ExecutorAction {
  public RunCurrentFileExecutorAction(@NotNull Executor executor) {
    super(executor);
  }

  @Override
  protected @Nullable RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
    return null; // null means 'run current file, not the selected run configuration'
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null || !RunConfigurationsComboBoxAction.hasRunCurrentFileItem(e.getProject())) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    super.update(e);

    // no need in a list of disabled actions in the secondary menu
    // of the 'Current File' item in the combo box drop-down menu
    if (!presentation.isEnabled() &&
        presentation.getClientProperty(WOULD_BE_ENABLED_BUT_STARTING) != Boolean.TRUE) {
      presentation.setVisible(false);
    }
  }
}
