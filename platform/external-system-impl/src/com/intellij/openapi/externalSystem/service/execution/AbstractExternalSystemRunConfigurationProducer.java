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
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 5/16/2016
 */
public class AbstractExternalSystemRunConfigurationProducer extends RunConfigurationProducer<ExternalSystemRunConfiguration> {

  public AbstractExternalSystemRunConfigurationProducer(@NotNull AbstractExternalSystemTaskConfigurationType type) {
    super(type);
  }

  @Override
  public boolean shouldReplace(ConfigurationFromContext self, ConfigurationFromContext other) {
    return true;
  }

  @Override
  protected boolean setupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    Location location = context.getLocation();
    if (!(location instanceof ExternalSystemTaskLocation)) {
      return false;
    }

    ExternalSystemTaskLocation taskLocation = (ExternalSystemTaskLocation)location;
    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    ExternalTaskExecutionInfo task = taskLocation.getTaskInfo();
    taskExecutionSettings.setExternalProjectPath(task.getSettings().getExternalProjectPath());
    taskExecutionSettings.setTaskNames(task.getSettings().getTaskNames());
    configuration.setName(AbstractExternalSystemTaskConfigurationType.generateName(location.getProject(), taskExecutionSettings));
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    final Location contextLocation = context.getLocation();
    if (contextLocation == null) return false;

    if (!(contextLocation instanceof ExternalSystemTaskLocation)) {
      return false;
    }

    ExternalSystemTaskLocation taskLocation = (ExternalSystemTaskLocation)contextLocation;
    ExternalSystemTaskExecutionSettings contextTaskExecutionSettings = taskLocation.getTaskInfo().getSettings();
    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    if (!StringUtil.equals(contextTaskExecutionSettings.getExternalProjectPath(), configuration.getSettings().getExternalProjectPath())) {
      return false;
    }
    if (!contextTaskExecutionSettings.getTaskNames().equals(taskExecutionSettings.getTaskNames())) return false;
    return true;
  }
}