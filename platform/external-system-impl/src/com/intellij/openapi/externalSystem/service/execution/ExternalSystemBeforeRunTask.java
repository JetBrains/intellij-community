/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.BeforeRunTask;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 5/30/2014
 */
public class ExternalSystemBeforeRunTask extends BeforeRunTask<ExternalSystemBeforeRunTask> {

  @NotNull
  private final ExternalSystemTaskExecutionSettings myTaskExecutionSettings;

  public ExternalSystemBeforeRunTask(@NotNull Key<ExternalSystemBeforeRunTask> providerId, @NotNull ProjectSystemId systemId) {
    super(providerId);
    myTaskExecutionSettings = new ExternalSystemTaskExecutionSettings();
    myTaskExecutionSettings.setExternalSystemIdString(systemId.getId());
  }

  @NotNull
  public ExternalSystemTaskExecutionSettings getTaskExecutionSettings() {
    return myTaskExecutionSettings;
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);

    element.setAttribute("tasks", StringUtil.join(myTaskExecutionSettings.getTaskNames(), " "));
    if (myTaskExecutionSettings.getExternalProjectPath() != null) {
      element.setAttribute("externalProjectPath", myTaskExecutionSettings.getExternalProjectPath());
    }
    if (myTaskExecutionSettings.getVmOptions() != null) element.setAttribute("vmOptions", myTaskExecutionSettings.getVmOptions());
    if (myTaskExecutionSettings.getScriptParameters() != null) {
      element.setAttribute("scriptParameters", myTaskExecutionSettings.getScriptParameters());
    }
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    myTaskExecutionSettings.setTaskNames(StringUtil.split(StringUtil.notNullize(element.getAttributeValue("tasks")), " "));
    myTaskExecutionSettings.setExternalProjectPath(element.getAttributeValue("externalProjectPath"));
    myTaskExecutionSettings.setVmOptions(element.getAttributeValue("vmOptions"));
    myTaskExecutionSettings.setScriptParameters(element.getAttributeValue("scriptParameters"));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ExternalSystemBeforeRunTask)) return false;
    if (!super.equals(o)) return false;

    ExternalSystemBeforeRunTask task = (ExternalSystemBeforeRunTask)o;

    if (!myTaskExecutionSettings.equals(task.myTaskExecutionSettings)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myTaskExecutionSettings.hashCode();
    return result;
  }
}
