// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Keeps external system task execution parameters. Basically, this is a model class which holds data represented when
 * a user opens run configuration editor for corresponding external system.
 */
@Tag("ExternalSystemSettings")
public class ExternalSystemTaskExecutionSettings implements Cloneable {
  @NotNull @NonNls public static final String TAG_NAME = "ExternalSystemSettings";
  @NotNull @NonNls public static final Key<ParametersList> JVM_AGENT_SETUP_KEY = Key.create("jvmAgentSetup");

  @NotNull
  private List<String> myTaskNames = new ArrayList<>();
  @NotNull
  private List<String> myTaskDescriptions = new ArrayList<>();

  @Nullable private String myExecutionName;
  private String myExternalSystemIdString;
  private String myExternalProjectPath;
  private String myVmOptions;
  private String myScriptParameters;
  @NotNull
  private Map<String, String> myEnv = new HashMap<>();
  private boolean myPassParentEnvs = true;

  public ExternalSystemTaskExecutionSettings() {
  }

  private ExternalSystemTaskExecutionSettings(@NotNull ExternalSystemTaskExecutionSettings source) {
    setFrom(source);
  }

  public void setFrom(@NotNull ExternalSystemTaskExecutionSettings source) {
    myExecutionName = source.myExecutionName;
    myExternalSystemIdString = source.myExternalSystemIdString;
    myExternalProjectPath = source.myExternalProjectPath;
    myVmOptions = source.myVmOptions;
    myScriptParameters = source.myScriptParameters;

    myTaskNames = ContainerUtil.copyList(source.myTaskNames);
    myTaskDescriptions = ContainerUtil.copyList(source.myTaskDescriptions);

    myEnv = source.myEnv.isEmpty() ? Collections.emptyMap() : new HashMap<>(source.myEnv);
    myPassParentEnvs = source.myPassParentEnvs;
  }

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

  @NotNull
  public List<String> getTaskNames() {
    return myTaskNames;
  }

  public void setTaskNames(@NotNull List<String> taskNames) {
    myTaskNames = taskNames;
  }

  @NotNull
  public List<String> getTaskDescriptions() {
    return myTaskDescriptions;
  }

  public void setTaskDescriptions(@NotNull List<String> taskDescriptions) {
    myTaskDescriptions = taskDescriptions;
  }

  @NotNull
  public Map<String, String> getEnv() {
    return myEnv;
  }

  public void setEnv(@NotNull Map<String, String> value) {
    myEnv = value;
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public ExternalSystemTaskExecutionSettings clone() {
    return new ExternalSystemTaskExecutionSettings(this);
  }

  @Override
  public int hashCode() {
    int result = myTaskNames.hashCode();
    result = 31 * result + (myExecutionName != null ? myExecutionName.hashCode() : 0);
    result = 31 * result + (myExternalSystemIdString != null ? myExternalSystemIdString.hashCode() : 0);
    result = 31 * result + (myExternalProjectPath != null ? myExternalProjectPath.hashCode() : 0);
    result = 31 * result + (myVmOptions != null ? myVmOptions.hashCode() : 0);
    result = 31 * result + (myScriptParameters != null ? myScriptParameters.hashCode() : 0);
    result = 31 * result + myEnv.hashCode();
    result = 31 * result + (myPassParentEnvs ? 1 : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemTaskExecutionSettings settings = (ExternalSystemTaskExecutionSettings)o;

    if (!Objects.equals(myExecutionName, settings.myExecutionName)) {
      return false;
    }

    if (!Objects.equals(myExternalProjectPath, settings.myExternalProjectPath))
    {
      return false;
    }
    if (!Objects.equals(myExternalSystemIdString, settings.myExternalSystemIdString))
    {
      return false;
    }
    if (!Objects.equals(myTaskNames, settings.myTaskNames)) return false;
    if (StringUtil.isEmpty(myVmOptions) ^ StringUtil.isEmpty(settings.myVmOptions)) return false;
    if (StringUtil.isEmpty(myScriptParameters) ^ StringUtil.isEmpty(settings.myScriptParameters)) return false;
    if (!Objects.equals(myEnv, settings.myEnv)) return false;
    if (myPassParentEnvs != settings.myPassParentEnvs) return false;
    return true;
  }

  @Override
  public String toString() {
    return StringUtil.join(myTaskNames, " ") +
           (StringUtil.isEmpty(myScriptParameters) ? "" : " " + myScriptParameters) +
           (StringUtil.isEmpty(myVmOptions) ? "" : " " + myVmOptions);
  }
}
