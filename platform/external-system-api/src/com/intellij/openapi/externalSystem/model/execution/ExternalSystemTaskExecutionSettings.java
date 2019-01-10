// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ViewableList;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * Keeps external system task execution parameters. Basically, this is a model class which holds data represented when
 * a user opens run configuration editor for corresponding external system.
 */
public class ExternalSystemTaskExecutionSettings implements Cloneable, TaskExecutionSettings {

  @NotNull @NonNls public static final Key<ParametersList> JVM_AGENT_SETUP_KEY = Key.create("jvmAgentSetup");

  @NotNull private List<TaskSettings> myTasksSettings = ContainerUtilRt.newArrayList();
  @NotNull private List<String> myTaskDescriptions = ContainerUtilRt.newArrayList();
  @NotNull private Set<String> myUnorderedParameters = ContainerUtilRt.newLinkedHashSet();

  @Nullable private String myExecutionName;
  private String myExternalSystemIdString;
  private String myExternalProjectPath;
  private String myVmOptions;
  private String myScriptParameters;
  @NotNull private Map<String, String> myEnv = ContainerUtilRt.newHashMap();
  private boolean myPassParentEnvs = true;

  public ExternalSystemTaskExecutionSettings() {
  }

  private ExternalSystemTaskExecutionSettings(@NotNull ExternalSystemTaskExecutionSettings source) {
    setFrom(source);
  }

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public void setFrom(@NotNull ExternalSystemTaskExecutionSettings source) {
    myExecutionName = source.myExecutionName;
    myExternalSystemIdString = source.myExternalSystemIdString;
    myExternalProjectPath = source.myExternalProjectPath;
    myVmOptions = source.myVmOptions;
    myScriptParameters = source.myScriptParameters;

    myTasksSettings = ContainerUtil.copyList(source.myTasksSettings);
    myTaskDescriptions = ContainerUtil.copyList(source.myTaskDescriptions);
    myUnorderedParameters = ContainerUtil.newLinkedHashSet(source.myUnorderedParameters);

    myEnv = source.myEnv.isEmpty() ? Collections.emptyMap() : new THashMap<>(source.myEnv);
    myPassParentEnvs = source.myPassParentEnvs;
  }

  @Override
  @Nullable
  public String getExecutionName() {
    return myExecutionName;
  }

  @Override
  public void setExecutionName(@Nullable String executionName) {
    myExecutionName = executionName;
  }

  public String getExternalSystemIdString() {
    return myExternalSystemIdString;
  }

  @Override
  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return new ProjectSystemId(myExternalSystemIdString);
  }

  @Override
  public void setExternalSystemId(@NotNull ProjectSystemId externalSystemId) {
    myExternalSystemIdString = externalSystemId.getId();
  }

  public void setExternalSystemIdString(String externalSystemIdString) {
    myExternalSystemIdString = externalSystemIdString;
  }

  @Override
  public String getExternalProjectPath() {
    return myExternalProjectPath;
  }

  @Override
  public void setExternalProjectPath(String externalProjectPath) {
    myExternalProjectPath = externalProjectPath;
  }

  @Override
  public String getVmOptions() {
    return myVmOptions;
  }

  @Override
  public void setVmOptions(String vmOptions) {
    myVmOptions = vmOptions;
  }

  @Override
  public void addTaskSettings(@NotNull TaskSettings taskSettings) {
    myTasksSettings.add(taskSettings);
  }

  @Override
  public void addUnorderedParameter(@NotNull String parameter) {
    myUnorderedParameters.add(parameter);
  }

  @Override
  @NotNull
  public @Unmodifiable List<TaskSettings> getTasksSettings() {
    return ContainerUtil.immutableList(myTasksSettings);
  }

  @Override
  public void resetTaskSettings() {
    myTasksSettings = ContainerUtilRt.newArrayList();
    myTaskDescriptions = ContainerUtilRt.newArrayList();
  }

  @Override
  public void resetUnorderedParameters() {
    myScriptParameters = "";
    myUnorderedParameters = ContainerUtilRt.newHashSet();
  }

  @Override
  public void removeUnorderedParameter(@NotNull String parameter) {
    if (myUnorderedParameters.remove(parameter)) return;
    List<String> parameters = StringUtil.splitHonorQuotes(myScriptParameters, ' ');
    parameters.remove(parameter);
    myScriptParameters = StringUtil.join(parameters, " ");
  }

  @Override
  @NotNull
  public @Unmodifiable Set<String> getUnorderedParameters() {
    List<String> parameters = StringUtil.splitHonorQuotes(myScriptParameters, ' ');
    parameters.addAll(myUnorderedParameters);
    return Collections.unmodifiableSet(ContainerUtil.newLinkedHashSet(parameters));
  }

  // Raw getters are deprecated because they are not part of the public API.
  // These is a part of a specific implementation.

  @Deprecated
  public String getRawScriptParameters() {
    return myScriptParameters;
  }

  @Deprecated
  public Set<String> getRawUnorderedArguments() {
    return Collections.unmodifiableSet(myUnorderedParameters);
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#getUnorderedParameters}
   */
  @Deprecated
  public String getScriptParameters() {
    StringJoiner joiner = new StringJoiner(" ");
    addScriptParameters(joiner);
    return StringUtil.nullize(joiner.toString());
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#addUnorderedParameter}
   * or {@link ExternalSystemTaskExecutionSettings#removeUnorderedParameter}
   * or {@link ExternalSystemTaskExecutionSettings#resetUnorderedParameters}
   */
  @Deprecated
  public void setScriptParameters(String scriptParameters) {
    resetUnorderedParameters();
    myScriptParameters = scriptParameters;
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#getTasksSettings}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public List<String> getTaskNames() {
    return new ViewableList<String>() {
      @Override
      public int getSize() {
        return getTasksSettingsWithTailTask().size();
      }

      @Override
      public void add(int index, String element) {
        TaskSettings settings = new TaskSettingsImpl(element);
        getTasksSettingsWithTailTask().add(index, settings);
      }

      @Override
      public String set(int index, String element) {
        TaskSettings settings = new TaskSettingsImpl(element);
        return getTasksSettingsWithTailTask().set(index, settings).getName();
      }

      @Override
      public String removeAt(int index) {
        return getTasksSettingsWithTailTask().remove(index).getName();
      }

      @Override
      public String get(int index) {
        return getTasksSettingsWithTailTask().get(index).getName();
      }
    };
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#addTaskSettings}
   * or {@link ExternalSystemTaskExecutionSettings#resetTaskSettings}
   */
  @Deprecated
  public void setTaskNames(@NotNull List<String> taskNames) {
    resetTaskSettings();
    for (String taskName : taskNames) {
      addTaskSettings(new TaskSettingsImpl(taskName));
    }
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#getTasksSettings}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public List<String> getTaskDescriptions() {
    return myTaskDescriptions;
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#addTaskSettings}
   * or {@link ExternalSystemTaskExecutionSettings#resetTaskSettings}
   */
  @Deprecated
  public void setTaskDescriptions(@NotNull List<String> taskDescriptions) {
    myTaskDescriptions = taskDescriptions;
  }

  @Override
  @NotNull
  public Map<String, String> getEnv() {
    return myEnv;
  }

  @Override
  public void setEnv(@NotNull Map<String, String> value) {
    myEnv = value;
  }

  @Override
  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
  }

  private int getTailTaskIndex() {
    for (int i = 0; i < myTasksSettings.size(); ++i) {
      TaskSettings settings = myTasksSettings.get(i);
      if (!settings.getArguments().isEmpty()) {
        return i;
      }
    }
    return myTasksSettings.size() - 1;
  }

  @Nullable
  private TaskSettings getTailTaskSettings() {
    int tailTaskIndex = getTailTaskIndex();
    if (tailTaskIndex < 0) return null;
    return myTasksSettings.get(tailTaskIndex);
  }

  private List<TaskSettings> getTasksSettingsAfterTailTask() {
    int tailTaskIndex = getTailTaskIndex();
    return myTasksSettings.subList(tailTaskIndex + 1, myTasksSettings.size());
  }

  private List<TaskSettings> getTasksSettingsWithTailTask() {
    int tailTaskIndex = getTailTaskIndex();
    return myTasksSettings.subList(0, tailTaskIndex + 1);
  }

  private void addTaskNames(StringJoiner joiner) {
    for (String name : getTaskNames()) {
      joiner.add(name);
    }
  }

  private void addScriptParameters(StringJoiner joiner) {
    TaskSettings tailTaskSettings = getTailTaskSettings();
    if (tailTaskSettings != null) {
      addAll(joiner, tailTaskSettings.getArguments());
    }
    for (TaskSettings settings : getTasksSettingsAfterTailTask()) {
      joiner.add(settings.getName());
      addAll(joiner, settings.getArguments());
    }
    addIfNotEmpty(joiner, myScriptParameters);
    addAll(joiner, myUnorderedParameters);
  }

  private void addCommandLineArguments(StringJoiner joiner) {
    addTaskNames(joiner);
    addScriptParameters(joiner);
  }

  @Override
  @NotNull
  public String toCommandLine() {
    StringJoiner joiner = new StringJoiner(" ");
    addCommandLineArguments(joiner);
    return joiner.toString();
  }

  @Override
  public String toString() {
    StringJoiner joiner = new StringJoiner(" ");
    addCommandLineArguments(joiner);
    addIfNotEmpty(joiner, myVmOptions);
    return joiner.toString();
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public ExternalSystemTaskExecutionSettings clone() {
    return new ExternalSystemTaskExecutionSettings(this);
  }

  @Override
  public int hashCode() {
    int result = toCommandLine().hashCode();
    result = 31 * result + (myExecutionName != null ? myExecutionName.hashCode() : 0);
    result = 31 * result + (myExternalSystemIdString != null ? myExternalSystemIdString.hashCode() : 0);
    result = 31 * result + (myExternalProjectPath != null ? myExternalProjectPath.hashCode() : 0);
    result = 31 * result + (myVmOptions != null ? myVmOptions.hashCode() : 0);
    result = 31 * result + myEnv.hashCode();
    result = 31 * result + (myPassParentEnvs ? 1 : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemTaskExecutionSettings settings = (ExternalSystemTaskExecutionSettings)o;

    if (!Objects.equals(myExecutionName, settings.myExecutionName)) return false;
    if (!Objects.equals(myExternalProjectPath, settings.myExternalProjectPath)) return false;
    if (!Objects.equals(myExternalSystemIdString, settings.myExternalSystemIdString)) return false;
    if (!Objects.equals(toCommandLine(), settings.toCommandLine())) return false;
    if (StringUtil.isEmpty(myVmOptions) ^ StringUtil.isEmpty(settings.myVmOptions)) return false;
    if (!Objects.equals(myEnv, settings.myEnv)) return false;
    if (myPassParentEnvs != settings.myPassParentEnvs) return false;
    return true;
  }

  private static void addAll(StringJoiner joiner, Iterable<String> elements) {
    for (String element : elements) {
      joiner.add(element);
    }
  }

  private static void addIfNotEmpty(StringJoiner joiner, String element) {
    if (StringUtil.isNotEmpty(element)) {
      joiner.add(element);
    }
  }
}
