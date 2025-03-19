// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemotePermissionRequirements;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

final class SaveTemporaryRunConfigurationAction
  extends RunConfigurationSpecificActionBase implements ActionRemotePermissionRequirements.WriteAccess {

  @Override
  protected void doUpdate(@NotNull AnActionEvent e,
                          @NotNull Project project,
                          @NotNull RunnerAndConfigurationSettings configuration) {
    Presentation presentation = e.getPresentation();
    presentation.setIcon(!ExperimentalUI.isNewUI() ? AllIcons.Actions.MenuSaveall : null);
    presentation.setEnabledAndVisible(configuration.isTemporary());
  }

  @Override
  protected void doActionPerformed(@NotNull Project project,
                                   @NotNull RunnerAndConfigurationSettings configuration) {
    RunManager.getInstance(project).makeStable(configuration);
  }
}
