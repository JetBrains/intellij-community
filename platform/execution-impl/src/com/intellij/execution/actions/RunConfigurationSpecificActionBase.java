// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ui.RunToolbarPopupKt;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class RunConfigurationSpecificActionBase extends AnAction {

  private static final Logger LOG = Logger.getInstance(RunConfigurationSpecificActionBase.class);

  protected abstract void doUpdate(@NotNull AnActionEvent e,
                                   @NotNull Project project,
                                   @NotNull RunnerAndConfigurationSettings configuration);

  protected abstract void doActionPerformed(@NotNull Project project,
                                            @NotNull RunnerAndConfigurationSettings configuration);

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      LOG.trace("project is not specified in data context");
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    RunnerAndConfigurationSettings configuration = e.getData(RunToolbarPopupKt.RUN_CONFIGURATION_KEY);
    if (configuration == null) {
      LOG.trace("run configuration information is not specified in data context");
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    doUpdate(e, project, configuration);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      LOG.warn("project is not specified in data context");
      return;
    }

    RunnerAndConfigurationSettings configuration = e.getData(RunToolbarPopupKt.RUN_CONFIGURATION_KEY);
    if (configuration == null) {
      LOG.warn("run configuration information is not specified in data context");
      return;
    }

    doActionPerformed(project, configuration);
  }
}
