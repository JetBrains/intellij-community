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
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
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
    ExternalTaskPojo task = taskLocation.getTask();
    taskExecutionSettings.setExternalProjectPath(task.getLinkedExternalProjectPath());
    taskExecutionSettings.setTaskNames(Collections.singletonList(task.getName()));
    // TODO den implement
    configuration.setName("xxx");
    return settings;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    return PREFERED;
  }
}
