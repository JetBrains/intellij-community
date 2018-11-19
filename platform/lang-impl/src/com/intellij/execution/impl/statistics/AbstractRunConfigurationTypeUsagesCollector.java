// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static java.lang.String.valueOf;

public abstract class AbstractRunConfigurationTypeUsagesCollector extends ProjectUsagesCollector {
  protected abstract boolean isApplicable(@NotNull RunManager runManager, @NotNull RunnerAndConfigurationSettings settings);

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    final TObjectIntHashMap<Template> templates = new TObjectIntHashMap<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
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
          if (configurationType instanceof UnknownConfigurationType) {
            continue;
          }
          final StringBuilder keyBuilder = new StringBuilder();
          keyBuilder.append(configurationType.getId());
          if (configurationType.getConfigurationFactories().length > 1) {
            keyBuilder.append(".").append(configurationFactory.getId());
          }
          final Template template = new Template(keyBuilder.toString(), createContext(settings, runConfiguration));
          if (templates.containsKey(template)) {
            templates.increment(template);
          }
          else {
            templates.put(template, 1);
          }
        }
      }
    });

    final Set<UsageDescriptor> result = new HashSet<>();
    templates.forEachEntry((template, value) -> result.add(template.createUsageDescriptor(value)));
    return result;
  }

  private static FUSUsageContext createContext(@NotNull RunnerAndConfigurationSettings settings,
                                               @NotNull RunConfiguration runConfiguration) {
    return FUSUsageContext.create(
      valueOf(settings.isShared()),
      valueOf(settings.isEditBeforeRun()),
      valueOf(settings.isActivateToolWindowBeforeRun()),
      valueOf(runConfiguration.isAllowRunningInParallel())
    );
  }

  private static class Template {
    private final String myKey;
    private final FUSUsageContext myContext;

    private Template(String key, FUSUsageContext context) {
      myKey = key;
      myContext = context;
    }

    private UsageDescriptor createUsageDescriptor(int count) {
      return new UsageDescriptor(myKey, count, myContext);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Template template = (Template)o;
      return Objects.equals(myKey, template.myKey) &&
             Objects.equals(myContext, template.myContext);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myKey, myContext);
    }
  }
}
