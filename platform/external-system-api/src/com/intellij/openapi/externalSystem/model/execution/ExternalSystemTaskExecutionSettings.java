// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
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

  static Logger LOG = Logger.getInstance(ExternalSystemTaskExecutionSettings.class);

  @NotNull @NonNls public static final Key<ParametersList> JVM_AGENT_SETUP_KEY = Key.create("jvmAgentSetup");

  @NotNull private List<TaskSettings> myTasksSettings = ContainerUtilRt.newArrayList();
  @NotNull private List<String> myTaskNames = ContainerUtilRt.newArrayList();
  @NotNull private List<String> myTaskDescriptions = ContainerUtilRt.newArrayList();
  @NotNull private Set<String> myUnorderedArguments = ContainerUtilRt.newLinkedHashSet();

  // needed for supporting old api
  @NotNull private List<String> myZipTaskArguments = ContainerUtilRt.newArrayList();

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
    myTaskNames = ContainerUtil.copyList(source.myTaskNames);
    myTaskDescriptions = ContainerUtil.copyList(source.myTaskDescriptions);
    myUnorderedArguments = ContainerUtil.newLinkedHashSet(source.myUnorderedArguments);

    myZipTaskArguments = source.myZipTaskArguments;

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
    repairSettingsIfNeeded();
    if (tryToAddTaskSettingsByOldApi(taskSettings)) return;
    myTasksSettings.add(taskSettings);
  }

  @Override
  public void addUnorderedArgument(@NotNull String argument) {
    myUnorderedArguments.add(argument);
  }

  @Override
  @NotNull
  public @Unmodifiable List<TaskSettings> getTasksSettings() {
    List<TaskSettings> tasksSettings = ContainerUtilRt.newArrayList();
    Iterator<String> taskNameIt = myTaskNames.iterator();
    Iterator<String> descriptionIt = myTaskDescriptions.iterator();
    while (taskNameIt.hasNext() && descriptionIt.hasNext()) {
      String name = taskNameIt.next();
      String description = descriptionIt.next();
      tasksSettings.add(new TaskSettingsImpl(name, description));
    }
    if (descriptionIt.hasNext()) {
      String formatLine = "inconsistent settings: descriptions.size [%d] > tasks.size [%d]";
      LOG.warn(String.format(formatLine, myTaskDescriptions.size(), myTaskNames.size()));
    }
    while (taskNameIt.hasNext()) {
      String name = taskNameIt.next();
      tasksSettings.add(new TaskSettingsImpl(name));
    }
    if (!tasksSettings.isEmpty()) {
      TaskSettings settings = tasksSettings.remove(tasksSettings.size() - 1);
      String name = settings.getName();
      String description = settings.getDescription();
      tasksSettings.add(new TaskSettingsImpl(name, myZipTaskArguments, description));
    }
    tasksSettings.addAll(myTasksSettings);
    return ContainerUtil.immutableList(tasksSettings);
  }

  @Override
  public void resetTaskSettings() {
    myTaskNames = ContainerUtilRt.newArrayList();
    myTasksSettings = ContainerUtilRt.newArrayList();
    myTaskDescriptions = ContainerUtilRt.newArrayList();
  }

  @Override
  public void resetUnorderedArguments() {
    myScriptParameters = "";
    myUnorderedArguments = ContainerUtilRt.newHashSet();
    myZipTaskArguments = ContainerUtilRt.newArrayList();
  }

  @Override
  public void removeUnorderedArgument(@NotNull String argument) {
    if (myUnorderedArguments.remove(argument)) return;
    List<String> parameters = StringUtil.split(myScriptParameters, " ");
    parameters.remove(argument);
    myScriptParameters = StringUtil.join(parameters, " ");
  }

  @Override
  @NotNull
  public @Unmodifiable Set<String> getUnorderedArguments() {
    List<String> parameters = StringUtil.splitHonorQuotes(myScriptParameters, ' ');
    parameters.addAll(myUnorderedArguments);
    return Collections.unmodifiableSet(ContainerUtil.newLinkedHashSet(parameters));
  }

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  // Needed for supporting old usages of {@link ExternalSystemTaskExecutionSettings#getTaskNames}
  private boolean tryToAddTaskSettingsByOldApi(TaskSettings taskSettings) {
    String name = taskSettings.getName();
    String description = taskSettings.getDescription();
    List<String> arguments = taskSettings.getArguments();
    if (description != null) return false;
    if (!myTasksSettings.isEmpty()) return false;
    if (!myZipTaskArguments.isEmpty()) return false;
    myZipTaskArguments = arguments;
    myTaskNames.add(name);
    return true;
  }

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public void repairSettingsIfNeeded() {
    boolean hasOldStructures = !myTaskNames.isEmpty() ||
                               !myTaskDescriptions.isEmpty();
    boolean hasNewStructures = !myTasksSettings.isEmpty();
    if (!hasOldStructures || !hasNewStructures) return;
    myTasksSettings = ContainerUtilRt.newArrayList(getTasksSettings());
    myTaskNames = ContainerUtilRt.newArrayList();
    myTaskDescriptions = ContainerUtilRt.newArrayList();
    myZipTaskArguments = ContainerUtilRt.newArrayList();
  }


  // Raw getters is deprecated because they are not part of the public API.
  // These is a part of a specific implementation.

  @Deprecated
  public List<String> getRawZipTaskArguments() {
    return ContainerUtil.immutableList(myZipTaskArguments);
  }

  @Deprecated
  public String getRawScriptParameters() {
    return myScriptParameters;
  }

  @Deprecated
  public Set<String> getRawUnorderedArguments() {
    return Collections.unmodifiableSet(myUnorderedArguments);
  }

  @Deprecated
  public List<TaskSettings> getRawTasksSettings() {
    return ContainerUtil.immutableList(myTasksSettings);
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#getUnorderedArguments}
   */
  @Deprecated
  public String getScriptParameters() {
    repairSettingsIfNeeded();
    StringJoiner joiner = new StringJoiner(" ");
    addScriptParameters(joiner);
    return StringUtil.nullize(joiner.toString());
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#addUnorderedArgument}
   * or {@link ExternalSystemTaskExecutionSettings#removeUnorderedArgument}
   * or {@link ExternalSystemTaskExecutionSettings#resetUnorderedArguments}
   */
  @Deprecated
  public void setScriptParameters(String scriptParameters) {
    resetUnorderedArguments();
    myScriptParameters = scriptParameters;
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#getTasksSettings}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public List<String> getTaskNames() {
    repairSettingsIfNeeded();
    return myTaskNames;
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#addTaskSettings}
   * or {@link ExternalSystemTaskExecutionSettings#resetTaskSettings}
   */
  @Deprecated
  public void setTaskNames(@NotNull List<String> taskNames) {
    myTaskNames = taskNames;
  }

  /**
   * Deprecated, instead use the {@link ExternalSystemTaskExecutionSettings#getTasksSettings}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public List<String> getTaskDescriptions() {
    repairSettingsIfNeeded();
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

  private void addScriptParameters(StringJoiner joiner) {
    addAll(joiner, myZipTaskArguments);
    for (TaskSettings settings : myTasksSettings) {
      joiner.add(settings.getName());
      addAll(joiner, settings.getArguments());
    }
    addIfNotEmpty(joiner, myScriptParameters);
    addAll(joiner, myUnorderedArguments);
  }

  private void addCommandLineArguments(StringJoiner joiner) {
    addAll(joiner, myTaskNames);
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
