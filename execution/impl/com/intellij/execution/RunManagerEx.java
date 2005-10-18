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

/**
 * Manages {@link RunConfiguration}s.
 *
 * @see ExecutionRegistry
 * @see ExecutionManager
 */
public abstract class RunManagerEx extends RunManager {
  public static RunManagerEx getInstanceEx(final Project project) {
    return (RunManagerEx)project.getComponent(RunManager.class);
  }

  public abstract RunnerAndConfigurationSettingsImpl getSelectedConfiguration(ConfigurationType type);



  public abstract boolean isTemporary(RunnerAndConfigurationSettingsImpl configuration);

  public abstract void setActiveConfiguration(RunnerAndConfigurationSettingsImpl configuration);

  public abstract void setSelectedConfiguration(RunnerAndConfigurationSettingsImpl configuration);

  public abstract void setTemporaryConfiguration(RunnerAndConfigurationSettingsImpl tempConfiguration);

  public abstract RunManagerConfig getConfig();

  public abstract RunnerAndConfigurationSettingsImpl createConfiguration(String name, ConfigurationFactory type);

  public abstract RunnerAndConfigurationSettingsImpl[] getConfigurationSettings(ConfigurationType type);

  public abstract void addConfiguration(RunnerAndConfigurationSettingsImpl settings);
}