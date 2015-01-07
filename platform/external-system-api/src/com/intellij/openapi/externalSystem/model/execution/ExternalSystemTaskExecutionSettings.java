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
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Keeps external system task execution parameters. Basically, this is a model class which holds data represented when
 * a user opens run configuration editor for corresponding external system.
 * 
 * @author Denis Zhdanov
 * @since 24.05.13 12:20
 */
@Tag("ExternalSystemSettings")
public class ExternalSystemTaskExecutionSettings implements Cloneable {

  @NotNull @NonNls public static final String TAG_NAME = "ExternalSystemSettings";

  private List<String> myTaskNames = ContainerUtilRt.newArrayList();
  private List<String> myTaskDescriptions = ContainerUtilRt.newArrayList();

  @Nullable private String myExecutionName;
  private String myExternalSystemIdString;
  private String myExternalProjectPath;
  private String myVmOptions;
  private String myScriptParameters;

  @Nullable
  public String getExecutionName() {
    return myExecutionName;
  }

  public void setExecutionName(@Nullable String executionName) {
    myExecutionName = executionName;
  }

  public String getExternalSystemIdString() {
    return myExternalSystemIdString;
  }

  public ProjectSystemId getExternalSystemId() {
    return new ProjectSystemId(myExternalSystemIdString);
  }

  public void setExternalSystemIdString(String externalSystemIdString) {
    myExternalSystemIdString = externalSystemIdString;
  }

  public String getExternalProjectPath() {
    return myExternalProjectPath;
  }

  public void setExternalProjectPath(String externalProjectPath) {
    myExternalProjectPath = externalProjectPath;
  }

  public String getVmOptions() {
    return myVmOptions;
  }

  public void setVmOptions(String vmOptions) {
    myVmOptions = vmOptions;
  }

  public String getScriptParameters() {
    return myScriptParameters;
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParameters = scriptParameters;
  }

  public List<String> getTaskNames() {
    return myTaskNames;
  }

  public void setTaskNames(List<String> taskNames) {
    myTaskNames = taskNames;
  }

  public List<String> getTaskDescriptions() {
    return myTaskDescriptions;
  }

  public void setTaskDescriptions(List<String> taskDescriptions) {
    myTaskDescriptions = taskDescriptions;
  }

  @Override
  public ExternalSystemTaskExecutionSettings clone() {
    ExternalSystemTaskExecutionSettings result = new ExternalSystemTaskExecutionSettings();
    result.setExecutionName(getExecutionName());
    result.setExternalSystemIdString(getExternalSystemIdString());
    result.setExternalProjectPath(getExternalProjectPath());
    result.setVmOptions(getVmOptions());
    result.setScriptParameters(getScriptParameters());
    result.setTaskNames(ContainerUtilRt.newArrayList(getTaskNames()));
    result.setTaskDescriptions(ContainerUtilRt.newArrayList(getTaskDescriptions()));
    return result;
  }

  @Override
  public int hashCode() {
    int result = myTaskNames != null ? myTaskNames.hashCode() : 0;
    result = 31 * result + (myExecutionName != null ? myExecutionName.hashCode() : 0);
    result = 31 * result + (myExternalSystemIdString != null ? myExternalSystemIdString.hashCode() : 0);
    result = 31 * result + (myExternalProjectPath != null ? myExternalProjectPath.hashCode() : 0);
    result = 31 * result + (myVmOptions != null ? myVmOptions.hashCode() : 0);
    result = 31 * result + (myScriptParameters != null ? myScriptParameters.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemTaskExecutionSettings settings = (ExternalSystemTaskExecutionSettings)o;

    if (myExecutionName != null ? !myExecutionName.equals(settings.myExecutionName) : settings.myExecutionName != null) {
      return false;
    }

    if (myExternalProjectPath != null
        ? !myExternalProjectPath.equals(settings.myExternalProjectPath)
        : settings.myExternalProjectPath != null)
    {
      return false;
    }
    if (myExternalSystemIdString != null
        ? !myExternalSystemIdString.equals(settings.myExternalSystemIdString)
        : settings.myExternalSystemIdString != null)
    {
      return false;
    }
    if (myTaskNames != null ? !myTaskNames.equals(settings.myTaskNames) : settings.myTaskNames != null) return false;
    if (StringUtil.isEmpty(myVmOptions) ^ StringUtil.isEmpty(settings.myVmOptions)) return false;
    if (StringUtil.isEmpty(myScriptParameters) ^ StringUtil.isEmpty(settings.myScriptParameters)) return false;

    return true;
  }

  @Override
  public String toString() {
    return (myTaskNames == null ? "" : StringUtil.join(myTaskNames, " ")) +
           (StringUtil.isEmpty(myScriptParameters) ? "" : " " + myScriptParameters) +
           (StringUtil.isEmpty(myVmOptions) ? "" : " " + myVmOptions);
  }
}
