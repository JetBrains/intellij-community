// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public abstract class AbstractRunConfigurationTypeUsagesCollector extends ProjectUsagesCollector {
  protected abstract boolean isApplicable(@NotNull RunManager runManager, @NotNull RunnerAndConfigurationSettings settings);

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    final Set<String> runConfigurationTypes = new HashSet<>();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      if (project.isDisposed()) return;
      final RunManager runManager = RunManager.getInstance(project);
      for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
        RunConfiguration runConfiguration = settings.getConfiguration();
        if (isApplicable(runManager, settings)) {
          final ConfigurationFactory configurationFactory = runConfiguration.getFactory();
          if (configurationFactory == null) {
            // not realistic
            continue;
          }

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
