// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.internal.statistic.IdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.statistics.ExternalSystemUsagesCollector.ExternalSystemTaskId.ExecuteTask;
import static com.intellij.openapi.externalSystem.statistics.ExternalSystemUsagesCollector.externalSystemTaskStarted;

/**
 * @author Denis Zhdanov
 */
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

  @SuppressWarnings("unchecked")
  @Override
  protected void doExecute() throws Exception {
    ExternalSystemProgressNotificationManagerImpl progressNotificationManager =
      (ExternalSystemProgressNotificationManagerImpl)ApplicationManager.getApplication()
        .getService(ExternalSystemProgressNotificationManager.class);
    ExternalSystemTaskId id = getId();
    String projectPath = getExternalProjectPath();

    ExternalSystemExecutionSettings settings;
    RemoteExternalSystemTaskManager taskManager;
    TargetEnvironmentConfigurationProvider environmentConfigurationProvider = null;
    try {
      progressNotificationManager.onStart(id, projectPath);

      Project project = getIdeProject();
      ProjectSystemId projectSystemId = getExternalSystemId();
      ExternalSystemTaskNotificationListener progressNotificationListener = wrapWithListener(progressNotificationManager);
      boolean isRunOnTargetsEnabled = Experiments.getInstance().isFeatureEnabled("run.targets");
      for (ExternalSystemExecutionAware executionAware : ExternalSystemExecutionAware.getExtensions(projectSystemId)) {
        executionAware.prepareExecution(this, projectPath, false, progressNotificationListener, project);

        if (!isRunOnTargetsEnabled || environmentConfigurationProvider != null) continue;
        environmentConfigurationProvider = executionAware.getEnvironmentConfigurationProvider(myConfiguration, project);
      }

      final ExternalSystemFacadeManager manager = ApplicationManager.getApplication().getService(ExternalSystemFacadeManager.class);
      settings = ExternalSystemApiUtil.getExecutionSettings(project, projectPath, projectSystemId);
      KeyFMap keyFMap = getUserMap();
      for (Key key : keyFMap.getKeys()) {
        settings.putUserData(key, keyFMap.get(key));
      }
      ExternalSystemExecutionAware.Companion.setEnvironmentConfigurationProvider(settings, environmentConfigurationProvider);

      RemoteExternalSystemFacade facade = manager.getFacade(project, projectPath, projectSystemId);
      taskManager = facade.getTaskManager();
      final List<String> vmOptions = parseCmdParameters(myVmOptions);
      final List<String> arguments = parseCmdParameters(myArguments);
      settings
        .withVmOptions(vmOptions)
        .withArguments(arguments)
        .withEnvironmentVariables(myEnv)
        .passParentEnvs(myPassParentEnvs);
    }
    catch (Exception e) {
      progressNotificationManager.onFailure(id, e);
      progressNotificationManager.onEnd(id);
      throw e;
    }

    IdeActivity activity =
      externalSystemTaskStarted(getIdeProject(), getExternalSystemId(), ExecuteTask, environmentConfigurationProvider);
    try {
      taskManager.executeTasks(id, myTasksToExecute, projectPath, settings, myJvmParametersSetup);
    }
    finally {
      activity.finished();
    }
  }

  @Override
  protected boolean doCancel() throws Exception {
    final ExternalSystemFacadeManager manager = ApplicationManager.getApplication().getService(ExternalSystemFacadeManager.class);
    RemoteExternalSystemFacade facade = manager.getFacade(getIdeProject(), getExternalProjectPath(), getExternalSystemId());
    RemoteExternalSystemTaskManager taskManager = facade.getTaskManager();

    return taskManager.cancelTask(getId());
  }

  private static List<String> parseCmdParameters(@Nullable String cmdArgsLine) {
    return cmdArgsLine != null ? ParametersListUtil.parse(cmdArgsLine, false, true) : new ArrayList<>();
  }
}
