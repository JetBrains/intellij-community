/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public abstract class RunManager {
  public static RunManager getInstance(final Project project) {
    return project.getComponent(RunManager.class);
  }

  @NotNull
  public abstract ConfigurationType[] getConfigurationFactories();

  @NotNull
  public abstract RunConfiguration[] getConfigurations(@NotNull ConfigurationType type);

  @NotNull
  public abstract RunConfiguration[] getAllConfigurations();

  @NotNull
  public abstract RunConfiguration[] getTempConfigurations();

  public abstract boolean isTemporary(@NotNull RunConfiguration configuration);

  public abstract void makeStable(@NotNull RunConfiguration configuration);

  @Nullable
  public abstract RunnerAndConfigurationSettings getSelectedConfiguration();

  @NotNull
  public abstract RunnerAndConfigurationSettings createRunConfiguration(@NotNull String name, @NotNull ConfigurationFactory type);

  @NotNull
  public abstract RunnerAndConfigurationSettings createConfiguration(RunConfiguration runConfiguration, ConfigurationFactory factory);

  @NotNull
  public abstract RunnerAndConfigurationSettings[] getConfigurationSettings(@NotNull ConfigurationType type);

  @NotNull
  public abstract Map<String, List<RunnerAndConfigurationSettings>> getStructure(@NotNull ConfigurationType type);

  public abstract void refreshUsagesList(RunProfile profile);
}
