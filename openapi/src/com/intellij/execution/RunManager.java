package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public abstract class RunManager {
  public static RunManager getInstance(final Project project) {
    return project.getComponent(RunManager.class);
  }
  public abstract ConfigurationType getActiveConfigurationFactory();

  public abstract ConfigurationType[] getConfigurationFactories();

  public abstract RunConfiguration[] getConfigurations(ConfigurationType type);

  public abstract RunConfiguration[] getAllConfigurations();

  public abstract RunConfiguration getTempConfiguration();

  public abstract boolean isTemporary(RunConfiguration configuration);

  public abstract void makeStable(RunConfiguration configuration);

  public abstract void setActiveConfigurationFactory(ConfigurationType activeConfigurationType);

  public abstract RunnerAndConfigurationSettings getSelectedConfiguration();

  public abstract RunnerAndConfigurationSettings createRunConfiguration(String name, ConfigurationFactory type);

}
