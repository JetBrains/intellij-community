/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 5/16/2016
 */
public abstract class AbstractExternalSystemRunConfigurationProducer extends RunConfigurationProducer<ExternalSystemRunConfiguration> {

  public AbstractExternalSystemRunConfigurationProducer(@NotNull AbstractExternalSystemTaskConfigurationType type) {
    super(type);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return true;
  }

  @Override
  protected boolean setupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    Project project = getProjectFromContext(context);
    if (project == null) return false;

    ExternalSystemTaskExecutionSettings contextTaskExecutionSettings = getTaskSettingsFromContext(context);
    if (contextTaskExecutionSettings == null) return false;

    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    if (!contextTaskExecutionSettings.getExternalSystemId().equals(taskExecutionSettings.getExternalSystemId())) {
      return false;
    }

    taskExecutionSettings.setExternalProjectPath(contextTaskExecutionSettings.getExternalProjectPath());
    taskExecutionSettings.setTaskNames(contextTaskExecutionSettings.getTaskNames());
    configuration.setName(AbstractExternalSystemTaskConfigurationType.generateName(project, taskExecutionSettings));
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    Project project = getProjectFromContext(context);
    if (project == null) return false;

    ExternalSystemTaskExecutionSettings contextTaskExecutionSettings = getTaskSettingsFromContext(context);
    if (contextTaskExecutionSettings == null) return false;

    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    if (!contextTaskExecutionSettings.getExternalSystemId().equals(taskExecutionSettings.getExternalSystemId())) {
      return false;
    }
    if (!StringUtil.equals(contextTaskExecutionSettings.getExternalProjectPath(), taskExecutionSettings.getExternalProjectPath())) {
      return false;
    }
    if (!contextTaskExecutionSettings.getTaskNames().equals(taskExecutionSettings.getTaskNames())) return false;
    return true;
  }

  @Nullable
  private static ExternalSystemTaskExecutionSettings getTaskSettingsFromContext(ConfigurationContext context) {
    final Location contextLocation = context.getLocation();
    if (!(contextLocation instanceof ExternalSystemTaskLocation)) {
      return null;
    }
    return ((ExternalSystemTaskLocation)contextLocation).getTaskInfo().getSettings();
  }

  @Nullable
  private static Project getProjectFromContext(ConfigurationContext context) {
    final Location contextLocation = context.getLocation();
    if (!(contextLocation instanceof ExternalSystemTaskLocation)) {
      return null;
    }
    return contextLocation.getProject();
  }
}