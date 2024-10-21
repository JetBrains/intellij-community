// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskCollector.externalSystemTaskStarted;
import static com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskId.ExecuteTask;

public class ExternalSystemExecuteTaskTask extends AbstractExternalSystemTask {

  @NotNull private final List<String> myTasksToExecute;
  @Nullable private final String myVmOptions;
  @Nullable private String myArguments;
  @Nullable private final String myJvmParametersSetup;
  private final boolean myPassParentEnvs;
  private final Map<String, String> myEnv;
  @NotNull private final ExternalSystemRunConfiguration myConfiguration;

  public ExternalSystemExecuteTaskTask(@NotNull Project project,
                                       @NotNull ExternalSystemTaskExecutionSettings settings,
                                       @Nullable String jvmParametersSetup,
                                       @NotNull ExternalSystemRunConfiguration configuration) throws IllegalArgumentException {
    super(settings.getExternalSystemId(), ExternalSystemTaskType.EXECUTE_TASK, project, settings.getExternalProjectPath());
    myTasksToExecute = new ArrayList<>(settings.getTaskNames());
    myVmOptions = settings.getVmOptions();
    myArguments = settings.getScriptParameters();
    myPassParentEnvs = settings.isPassParentEnvs();
    myEnv = settings.getEnv();
    myJvmParametersSetup = jvmParametersSetup;
    myConfiguration = configuration;

    configuration.copyUserDataTo(this);
  }

  @NotNull
  public List<String> getTasksToExecute() {
    return myTasksToExecute;
  }

  @Nullable
  public String getVmOptions() {
    return myVmOptions;
  }

  @Nullable
  public String getArguments() {
    return myArguments;
  }

  public void appendArguments(@NotNull String arguments) {
    myArguments = myArguments == null ? arguments : myArguments + ' ' + arguments;
  }

  @Override
  protected void doExecute() throws Exception {
    var project = getIdeProject();
    var projectSystemId = getExternalSystemId();
    var projectPath = getExternalProjectPath();

    var progressNotificationManager = ExternalSystemProgressNotificationManagerImpl.getInstanceImpl();
    var progressNotificationListener = wrapWithListener(progressNotificationManager);
    for (var executionAware : ExternalSystemExecutionAware.getExtensions(projectSystemId)) {
      executionAware.prepareExecution(this, projectPath, false, progressNotificationListener, project);
    }

    var settings = ExternalSystemApiUtil.getExecutionSettings(project, projectPath, projectSystemId)
      .withVmOptions(parseCmdParameters(myVmOptions))
      .withArguments(parseCmdParameters(myArguments))
      .withEnvironmentVariables(myEnv)
      .passParentEnvs(myPassParentEnvs);

    settings.setTasks(myTasksToExecute);
    settings.setJvmParameters(myJvmParametersSetup);

    putUserDataTo(settings);

    TargetEnvironmentConfigurationProvider environmentConfigurationProvider = null;
    for (var executionAware : ExternalSystemExecutionAware.getExtensions(projectSystemId)) {
      if (environmentConfigurationProvider == null) {
        environmentConfigurationProvider = executionAware.getEnvironmentConfigurationProvider(myConfiguration, project);
      }
    }
    ExternalSystemExecutionAware.setEnvironmentConfigurationProvider(settings, environmentConfigurationProvider);

    executeTasks(settings);
  }

  private void executeTasks(@NotNull ExternalSystemExecutionSettings settings) throws Exception {
    var id = getId();
    var project = getIdeProject();
    var projectSystemId = getExternalSystemId();
    var projectPath = getExternalProjectPath();

    var environmentConfigurationProvider = ExternalSystemExecutionAware.getEnvironmentConfigurationProvider(settings);
    var activity = externalSystemTaskStarted(project, projectSystemId, ExecuteTask, environmentConfigurationProvider);
    try {
      var manager = ExternalSystemFacadeManager.getInstance();
      var facade = manager.getFacade(project, projectPath, projectSystemId);
      var taskManager = facade.getTaskManager();
      //noinspection unchecked
      taskManager.executeTasks(projectPath, id, settings);
    }
    finally {
      activity.finished();
    }
  }

  @Override
  protected boolean doCancel() throws Exception {
    var manager = ExternalSystemFacadeManager.getInstance();
    var facade = manager.getFacade(getIdeProject(), getExternalProjectPath(), getExternalSystemId());
    var taskManager = facade.getTaskManager();
    return taskManager.cancelTask(getId());
  }

  private static List<String> parseCmdParameters(@Nullable String cmdArgsLine) {
    return cmdArgsLine != null ? ParametersListUtil.parse(cmdArgsLine, false, true) : new ArrayList<>();
  }
}
