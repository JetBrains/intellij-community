// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.view;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;

import static com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.getRunConfigurationActivationTaskName;

/**
 * @author Vladislav.Soroka
 */
public class RunConfigurationNode extends ExternalSystemNode {
  private final RunnerAndConfigurationSettings mySettings;

  @ApiStatus.Internal
  public RunConfigurationNode(@NotNull ExternalProjectsView externalProjectsView,
                              RunConfigurationsNode parent,
                              @NotNull RunnerAndConfigurationSettings settings) {
    super(externalProjectsView, parent);
    mySettings = settings;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    super.update(presentation);
    presentation.setIcon(ProgramRunnerUtil.getConfigurationIcon(mySettings, false));

    final ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)mySettings.getConfiguration();
    final ExternalSystemTaskExecutionSettings taskExecutionSettings = runConfiguration.getSettings();
    final String shortcutHint = StringUtil.nullize(getShortcutsManager().getDescription(
      taskExecutionSettings.getExternalProjectPath(), mySettings.getName()));
    final String activatorHint = StringUtil.nullize(getTaskActivator().getDescription(
      taskExecutionSettings.getExternalSystemId(), taskExecutionSettings.getExternalProjectPath(),
      getRunConfigurationActivationTaskName(mySettings)));

    String hint;
    if (shortcutHint == null) {
      hint = activatorHint;
    }
    else if (activatorHint == null) {
      hint = shortcutHint;
    }
    else {
      hint = shortcutHint + ", " + activatorHint;
    }

    setNameAndTooltip(presentation, getName(), StringUtil.join(taskExecutionSettings.getTaskNames(), " "), hint);
  }

  public RunnerAndConfigurationSettings getSettings() {
    return mySettings;
  }

  @Override
  public String getName() {
    return mySettings.getName();
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }

  @Override
  protected @Nullable String getMenuId() {
    return "ExternalSystemView.RunConfigurationMenu";
  }

  public void updateRunConfiguration() {
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    ExternalProjectsView projectsView = getExternalProjectsView();
    String place = projectsView instanceof Component ? ((Component)projectsView).getName() : "unknown";

    ExternalSystemActionsCollector.trigger(myProject, projectsView.getSystemId(),
                                           ExternalSystemActionsCollector.ActionId.ExecuteExternalSystemRunConfigurationAction,
                                           place, false, null);
    ProgramRunnerUtil.executeConfiguration(mySettings, DefaultRunExecutor.getRunExecutorInstance());
    RunManager runManager = RunManager.getInstance(mySettings.getConfiguration().getProject());
    runManager.addConfiguration(mySettings);
    runManager.setSelectedConfiguration(mySettings);
  }

  @Override
  public @Nullable Navigatable getNavigatable() {
    return new Navigatable() {

      @Override
      public void navigate(boolean requestFocus) {
        RunManager.getInstance(myProject).setSelectedConfiguration(mySettings);
        EditConfigurationsDialog dialog = new EditConfigurationsDialog(myProject);
        dialog.show();
      }

      @Override
      public boolean canNavigate() {
        return true;
      }
    };
  }
}
