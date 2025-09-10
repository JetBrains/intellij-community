// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.core.permissions.Permission;
import com.intellij.platform.ide.core.permissions.RequiresPermissions;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.FilePermissionsKt.getProjectFilesWrite;

final class DeleteRunConfigurationAction extends RunConfigurationSpecificActionBase implements RequiresPermissions, DumbAware {

  @Override
  protected void doUpdate(@NotNull AnActionEvent e,
                          @NotNull Project project,
                          @NotNull RunnerAndConfigurationSettings configuration) {
    Presentation presentation = e.getPresentation();
    presentation.setIcon(!ExperimentalUI.isNewUI() ? AllIcons.Actions.Cancel : null);
    presentation.setEnabledAndVisible(true);
  }

  @Override
  protected void doActionPerformed(@NotNull Project project,
                                   @NotNull RunnerAndConfigurationSettings configuration) {
    ChooseRunConfigurationManager.deleteConfiguration(project, configuration, null);
  }

  @Override
  public @NotNull Collection<@NotNull Permission> getRequiredPermissions() {
    return List.of(getProjectFilesWrite());
  }
}
