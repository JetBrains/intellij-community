package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.JavaProgramRunner;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public interface RunnerAndConfigurationSettings {
  ConfigurationFactory getFactory();

  boolean isTemplate();

  RunConfiguration getConfiguration();

  void setName(String name);

  String getName();

  RunnerSettings getRunnerSettings(JavaProgramRunner runner);

  ConfigurationPerRunnerSettings getConfigurationSettings(JavaProgramRunner runner);

  ConfigurationType getType();
}
