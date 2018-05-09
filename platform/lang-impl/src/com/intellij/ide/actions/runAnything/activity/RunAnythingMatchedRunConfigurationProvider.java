// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

/**
 * Implement this class if a particular run configuration should be created for matching input string.
 */
public abstract class RunAnythingMatchedRunConfigurationProvider extends RunAnythingRunConfigurationExecutionProvider
  implements RunAnythingHelpProviderBase<ChooseRunConfigurationPopup.ItemWrapper>,
             RunAnythingSingleParametrizedExecutionProvider<ChooseRunConfigurationPopup.ItemWrapper> {
  /**
   * Actual run configuration creation by {@code commandLine}
   *
   * @param dataContext
   * @param pattern
   * @return created run configuration
   */
  @NotNull
  public abstract RunnerAndConfigurationSettings createConfiguration(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Returns current provider associated run configuration factory
   */
  @NotNull
  public abstract ConfigurationFactory getConfigurationFactory();

  @Override
  public Icon getIcon() {
    return getConfigurationFactory().getIcon();
  }

  @NotNull
  @Override
  public ChooseRunConfigurationPopup.ItemWrapper getValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return ChooseRunConfigurationPopup.ItemWrapper.wrap(fetchProject(dataContext), createConfiguration(dataContext, pattern));
  }
}