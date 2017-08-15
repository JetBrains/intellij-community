/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public abstract class AbstractRunConfigurationTypeUsagesCollector extends AbstractProjectsUsagesCollector {

  protected abstract boolean isApplicable(@NotNull RunManager runManager, @NotNull RunnerAndConfigurationSettings settings);

  @NotNull
  @Override
  public final Set<UsageDescriptor> getProjectUsages(@NotNull final Project project) {
    final Set<String> runConfigurationTypes = new HashSet<>();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      if (project.isDisposed()) return;
      final RunManager runManager = RunManager.getInstance(project);
      for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
        RunConfiguration runConfiguration = settings.getConfiguration();
        if (isApplicable(runManager, settings)) {
          final ConfigurationFactory configurationFactory = runConfiguration.getFactory();
          final ConfigurationType configurationType = configurationFactory.getType();
          final StringBuilder keyBuilder = new StringBuilder();
          keyBuilder.append(configurationType.getId());
          if (configurationType.getConfigurationFactories().length > 1) {
            keyBuilder.append(".").append(configurationFactory.getId());
          }
          runConfigurationTypes.add(keyBuilder.toString());
        }
      }
    });
    return ContainerUtil.map2Set(runConfigurationTypes, runConfigurationType -> new UsageDescriptor(runConfigurationType, 1));
  }
}
