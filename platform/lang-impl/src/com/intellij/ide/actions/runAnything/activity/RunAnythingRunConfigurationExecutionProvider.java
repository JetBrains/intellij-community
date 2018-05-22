// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.ide.actions.runAnything.RunAnythingRunConfigurationItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ide.actions.runAnything.RunAnythingAction.EXECUTOR_KEY;
import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public abstract class RunAnythingRunConfigurationExecutionProvider
  extends RunAnythingProviderBase<ChooseRunConfigurationPopup.ItemWrapper> {

  @NotNull
  @Override
  public String getCommand(@NotNull ChooseRunConfigurationPopup.ItemWrapper value) {
    Object runConfiguration = value.getValue();
    if (!(runConfiguration instanceof RunnerAndConfigurationSettings)) {
      return value.getText();
    }
    return ((RunnerAndConfigurationSettings)runConfiguration).getName();
  }

  public void execute(@NotNull DataContext dataContext, @NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    RunnerAndConfigurationSettings configuration = (RunnerAndConfigurationSettings)wrapper.getValue();
    Project project = fetchProject(dataContext);

    RunManagerEx.getInstanceEx(project).setTemporaryConfiguration(configuration);
    RunManager.getInstance(project).setSelectedConfiguration(configuration);

    Executor executor = EXECUTOR_KEY.getData(dataContext);
    assert executor != null;
    Object settings = wrapper.getValue();
    if (settings instanceof RunnerAndConfigurationSettings) {
      ExecutionUtil.runConfiguration((RunnerAndConfigurationSettings)settings, executor);
    }
  }

  @Nullable
  @Override
  public Icon getIcon(@NotNull ChooseRunConfigurationPopup.ItemWrapper value) {
    return value.getIcon();
  }

  @Nullable
  @Override
  public String getAdText() {
    return RunAnythingRunConfigurationItem.RUN_CONFIGURATION_AD_TEXT;
  }

  @NotNull
  @Override
  public RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull ChooseRunConfigurationPopup.ItemWrapper value) {
    return new RunAnythingRunConfigurationItem(value, value.getIcon());
  }
}