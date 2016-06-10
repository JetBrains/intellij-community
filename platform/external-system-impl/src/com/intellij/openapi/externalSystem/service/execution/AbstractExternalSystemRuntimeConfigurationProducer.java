/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated please use {@link AbstractExternalSystemRunConfigurationProducer} instead
 * @author Denis Zhdanov
 * @since 6/5/13 8:14 PM
 */
public abstract class AbstractExternalSystemRuntimeConfigurationProducer extends RuntimeConfigurationProducer {

  private PsiElement mySourceElement;
  
  public AbstractExternalSystemRuntimeConfigurationProducer(@NotNull AbstractExternalSystemTaskConfigurationType type) {
    super(type);
  }

  @Override
  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  @Nullable
  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    if (!(location instanceof ExternalSystemTaskLocation)) {
      return null;
    }
    
    ExternalSystemTaskLocation taskLocation = (ExternalSystemTaskLocation)location;
    mySourceElement = taskLocation.getPsiElement();

    RunManagerEx runManager = RunManagerEx.getInstanceEx(taskLocation.getProject());
    RunnerAndConfigurationSettings settings = runManager.createConfiguration("", getConfigurationFactory());
    ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)settings.getConfiguration();
    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    ExternalTaskExecutionInfo task = taskLocation.getTaskInfo();
    taskExecutionSettings.setExternalProjectPath(task.getSettings().getExternalProjectPath());
    taskExecutionSettings.setTaskNames(task.getSettings().getTaskNames());
    configuration.setName(AbstractExternalSystemTaskConfigurationType.generateName(location.getProject(), taskExecutionSettings));
    return settings;
  }

  @Nullable
  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull List<RunnerAndConfigurationSettings> existingConfigurationsSettings,
                                                                 ConfigurationContext context) {
    if (!(location instanceof ExternalSystemTaskLocation)) {
      return null;
    }
    ExternalTaskExecutionInfo taskInfo = ((ExternalSystemTaskLocation)location).getTaskInfo();

    for (RunnerAndConfigurationSettings settings : existingConfigurationsSettings) {
      RunConfiguration runConfiguration = settings.getConfiguration();
      if (!(runConfiguration instanceof ExternalSystemRunConfiguration)) {
        continue;
      }
      if (match(taskInfo, ((ExternalSystemRunConfiguration)runConfiguration).getSettings())) {
        return settings;
      }
    }
    return null;
  }

  private static boolean match(@NotNull ExternalTaskExecutionInfo task, @NotNull ExternalSystemTaskExecutionSettings settings) {
    if (!task.getSettings().getExternalProjectPath().equals(settings.getExternalProjectPath())) {
      return false;
    }
    List<String> taskNames = settings.getTaskNames();
    return task.getSettings().getTaskNames().equals(taskNames);
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return PREFERED;
  }
}
