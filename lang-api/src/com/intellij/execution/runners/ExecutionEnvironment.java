package com.intellij.execution.runners;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class ExecutionEnvironment {
  private DataContext myDataContext;
  private RunProfile myRunProfile;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  public ExecutionEnvironment() {
  }

  public ExecutionEnvironment(@NotNull final ProgramRunner runner, @NotNull final RunnerAndConfigurationSettings configuration, final DataContext context) {
    this(configuration.getConfiguration(), configuration.getRunnerSettings(runner), configuration.getConfigurationSettings(runner), context);
  }

  public ExecutionEnvironment(@NotNull final RunProfile profile, final DataContext dataContext) {
    myRunProfile = profile;
    myDataContext = dataContext;
  }

  public ExecutionEnvironment(@NotNull final RunProfile runProfile,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings,
                              final DataContext dataContext) {
    this(runProfile, dataContext);
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  @NotNull
  public RunProfile getRunProfile() {
    return myRunProfile;
  }

  public DataContext getDataContext() {
    return myDataContext;
  }

  @Nullable
  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  public RunProfileState getState(final Executor executor) throws ExecutionException {
    return myRunProfile.getState(executor, this);
  }
}
