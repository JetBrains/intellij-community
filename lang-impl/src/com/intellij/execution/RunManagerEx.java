/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Manages {@link RunConfiguration}s.
 *
 * @see RunnerRegistry
 * @see ExecutionManager
 */
public abstract class RunManagerEx extends RunManager {
  public static RunManagerEx getInstanceEx(final Project project) {
    return (RunManagerEx)project.getComponent(RunManager.class);
  }

  @Nullable
  public abstract RunnerAndConfigurationSettingsImpl getSelectedConfiguration();

  public abstract boolean isTemporary(RunnerAndConfigurationSettingsImpl configuration);

  public abstract void setActiveConfiguration(RunnerAndConfigurationSettingsImpl configuration);

  public abstract void setSelectedConfiguration(RunnerAndConfigurationSettingsImpl configuration);

  public abstract void setTemporaryConfiguration(RunnerAndConfigurationSettingsImpl tempConfiguration);

  public abstract RunManagerConfig getConfig();

  @NotNull
  public abstract RunnerAndConfigurationSettingsImpl createConfiguration(String name, ConfigurationFactory type);

  @NotNull
  public abstract RunnerAndConfigurationSettingsImpl createConfiguration(RunConfiguration runConfiguration, ConfigurationFactory factory);

  public abstract RunnerAndConfigurationSettingsImpl[] getConfigurationSettings(ConfigurationType type);

  public abstract void addConfiguration(RunnerAndConfigurationSettingsImpl settings, boolean isShared, Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks);

  public abstract void addConfiguration(final RunnerAndConfigurationSettingsImpl settings, final boolean isShared);

  public abstract boolean isConfigurationShared(RunnerAndConfigurationSettingsImpl settings);

  @NotNull
  public abstract <T extends BeforeRunTask> Map<Key<T>, BeforeRunTask> getBeforeRunTasks(RunConfiguration settings);

  @Nullable
  public abstract <T extends BeforeRunTask> T getBeforeRunTask(RunConfiguration settings, Key<T> taskProviderID);

  @NotNull
  public abstract <T extends BeforeRunTask> Collection<T> getBeforeRunTasks(Key<T> taskProviderID, boolean includeOnlyActiveTasks);

  public abstract RunnerAndConfigurationSettingsImpl findConfigurationByName(@NotNull final String name);

  public abstract Collection<RunnerAndConfigurationSettingsImpl> getSortedConfigurations();

  public abstract void removeConfiguration(RunnerAndConfigurationSettingsImpl settings);
}