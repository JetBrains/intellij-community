/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;

import java.util.Set;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public abstract class RunManager {
  public static RunManager getInstance(final Project project) {
    return project.getComponent(RunManager.class);
  }

  public abstract ConfigurationType[] getConfigurationFactories();

  public abstract RunConfiguration[] getConfigurations(ConfigurationType type);

  public abstract RunConfiguration[] getAllConfigurations();

  public abstract RunConfiguration getTempConfiguration();

  public abstract boolean isTemporary(RunConfiguration configuration);

  public abstract void makeStable(RunConfiguration configuration);

  public abstract RunnerAndConfigurationSettings getSelectedConfiguration();

  public abstract RunnerAndConfigurationSettings createRunConfiguration(String name, ConfigurationFactory type);

  public abstract void registerActionBeforeRun(String actionName, Function<RunConfiguration, String> action, Function<RunConfiguration, String> retrieveDescription);

  public abstract Set<String> getPossibleActionsBeforeRun();

  public abstract Function<RunConfiguration, String> getActionByName(String actionName);

  public abstract String getDescriptionByName(String actionName, RunConfiguration runConfiguration);
}
