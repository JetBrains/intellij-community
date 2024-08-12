// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector;
import com.intellij.ide.actions.runAnything.RunAnythingRunConfigurationItem;
import com.intellij.ide.actions.runAnything.RunAnythingUtil;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ide.actions.runAnything.RunAnythingAction.EXECUTOR_KEY;
import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public abstract class RunAnythingRunConfigurationProvider extends RunAnythingProviderBase<ChooseRunConfigurationPopup.ItemWrapper> {
  @Override
  public @NotNull String getCommand(@NotNull ChooseRunConfigurationPopup.ItemWrapper value) {
    return value.getText();
  }

  @Override
  public void execute(@NotNull DataContext dataContext, @NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    Executor executor = EXECUTOR_KEY.getData(dataContext);
    assert executor != null;

    Object value = wrapper.getValue();
    Project project = fetchProject(dataContext);
    if (value instanceof RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
      if (!RunManager.getInstance(project).hasSettings(runnerAndConfigurationSettings)) {
        RunManager.getInstance(project).addConfiguration(runnerAndConfigurationSettings);
        RunConfigurationOptionUsagesCollector.logAddNew(
          project, runnerAndConfigurationSettings.getType().getId(), ActionPlaces.RUN_ANYTHING_POPUP);
      }
    }

    wrapper.perform(project, executor, dataContext);
  }

  @Override
  public @Nullable Icon getIcon(@NotNull ChooseRunConfigurationPopup.ItemWrapper value) {
    return value.getIcon();
  }

  @Override
  public @Nullable String getAdText() {
    return RunAnythingUtil.getAdContextText() + ", " + RunAnythingUtil.getAdDebugText();
  }

  @Override
  public @NotNull RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull ChooseRunConfigurationPopup.ItemWrapper value) {
    return new RunAnythingRunConfigurationItem(value, value.getIcon());
  }
}