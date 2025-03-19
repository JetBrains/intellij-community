// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecImpl;
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.UnindexedFilesScannerExecutor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskCollector.externalSystemTaskStarted;
import static com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskId.ResolveProject;

/**
 * Thread-safe.
 */
public class ExternalSystemResolveProjectTask extends AbstractExternalSystemTask {

  private final AtomicReference<DataNode<ProjectData>> myExternalProject = new AtomicReference<>();
  private final @NotNull @Nls String myProjectName;
  private final boolean myIsPreviewMode;
  private final @Nullable String myVmOptions;
  private final @Nullable String myArguments;
  private final @Nullable ProjectResolverPolicy myResolverPolicy;

  public ExternalSystemResolveProjectTask(
    @NotNull Project project,
    @NotNull String projectPath,
    @NotNull ImportSpec importSpec
  ) {
    super(importSpec.getExternalSystemId(), ExternalSystemTaskType.RESOLVE_PROJECT, project, projectPath);
    myProjectName = generateProjectName(projectPath);
    myIsPreviewMode = importSpec.isPreviewMode();
    myVmOptions = importSpec.getVmOptions();
    myArguments = importSpec.getArguments();
    myResolverPolicy = importSpec instanceof ImportSpecImpl ? ((ImportSpecImpl)importSpec).getProjectResolverPolicy() : null;
    UserDataHolderBase userData = importSpec.getUserData();
    if (userData != null) {
      userData.copyUserDataTo(this);
    }
  }

  @Override
  protected void doExecute() throws Exception {
    var project = getIdeProject();
    var projectSystemId = getExternalSystemId();
    var projectPath = getExternalProjectPath();

    var progressNotificationManager = ExternalSystemProgressNotificationManagerImpl.getInstanceImpl();
    var progressNotificationListener = wrapWithListener(progressNotificationManager);
    for (var executionAware : ExternalSystemExecutionAware.getExtensions(projectSystemId)) {
      executionAware.prepareExecution(this, projectPath, myIsPreviewMode, progressNotificationListener, project);
    }

    var settings = ExternalSystemApiUtil.getExecutionSettings(project, projectPath, projectSystemId);
    if (StringUtil.isNotEmpty(myVmOptions)) {
      settings.withVmOptions(ParametersListUtil.parse(myVmOptions));
    }
    if (StringUtil.isNotEmpty(myArguments)) {
      settings.withArguments(ParametersListUtil.parse(myArguments));
    }

    putUserDataTo(settings);

    TargetEnvironmentConfigurationProvider environmentConfigurationProvider = null;
    for (var executionAware : ExternalSystemExecutionAware.getExtensions(projectSystemId)) {
      if (environmentConfigurationProvider == null) {
        environmentConfigurationProvider = executionAware.getEnvironmentConfigurationProvider(projectPath, myIsPreviewMode, project);
      }
    }
    ExternalSystemExecutionAware.setEnvironmentConfigurationProvider(settings, environmentConfigurationProvider);

    resolveProjectInfo(settings);
  }

  private void resolveProjectInfo(@NotNull ExternalSystemExecutionSettings settings) {
    var id = getId();
    var project = getIdeProject();
    var projectSystemId = getExternalSystemId();
    var projectPath = getExternalProjectPath();

    var environmentConfigurationProvider = ExternalSystemExecutionAware.getEnvironmentConfigurationProvider(settings);
    var activity = externalSystemTaskStarted(project, projectSystemId, ResolveProject, environmentConfigurationProvider);
    try {
      DataNode<ProjectData> projectNode = suspendScanningAndIndexingThenRun(() -> {
        try {
          var manager = ExternalSystemFacadeManager.getInstance();
          var facade = manager.getFacade(project, projectPath, projectSystemId);
          var resolver = facade.getResolver();
          //noinspection unchecked
          return (DataNode<ProjectData>)resolver.resolveProjectInfo(id, projectPath, myIsPreviewMode, settings, myResolverPolicy);
        }
        catch (RemoteException e) {
          throw new RuntimeException(e);
        }
      });

      if (projectNode != null) {
        myExternalProject.set(projectNode);

        var externalSystemManager = ExternalSystemApiUtil.getManager(projectSystemId);
        assert externalSystemManager != null;

        var externalModulePaths = new HashSet<String>();
        var moduleNodes = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE);
        for (DataNode<ModuleData> node : moduleNodes) {
          externalModulePaths.add(node.getData().getLinkedExternalProjectPath());
        }
        var ExternalProjectPath = projectNode.getData().getLinkedExternalProjectPath();
        var externalSystemSettings = externalSystemManager.getSettingsProvider().fun(project);
        var externalProjectSettings = externalSystemSettings.getLinkedProjectSettings(ExternalProjectPath);
        if (externalProjectSettings != null && !externalModulePaths.isEmpty()) {
          externalProjectSettings.setModules(externalModulePaths);
        }
      }
    }
    finally {
      activity.finished();
    }
  }

  private <R> R suspendScanningAndIndexingThenRun(@NotNull Supplier<R> computable) {
    if (!Registry.is("external.system.pause.indexing.during.sync")) {
      return computable.get();
    }

    Ref<R> result = new Ref<>();

    String title = ExternalSystemBundle.message("progress.refresh.text", myProjectName, getExternalSystemId().getReadableName());
    UnindexedFilesScannerExecutor.getInstance(getIdeProject()).suspendScanningAndIndexingThenRun(title, () -> {
      result.set(computable.get());
    });

    return result.get();
  }

  @Override
  protected boolean doCancel() throws Exception {
    var manager = ExternalSystemFacadeManager.getInstance();
    var facade = manager.getFacade(getIdeProject(), getExternalProjectPath(), getExternalSystemId());
    var resolver = facade.getResolver();
    return resolver.cancelTask(getId());
  }

  @Override
  protected @NotNull String wrapProgressText(@NotNull String text) {
    return ExternalSystemBundle.message("progress.update.text", getExternalSystemId().getReadableName(), text);
  }

  public boolean isPreviewMode() {
    return myIsPreviewMode;
  }

  public @Nullable ProjectResolverPolicy getResolverPolicy() {
    return myResolverPolicy;
  }

  public @NotNull @Nls String getProjectName() {
    return myProjectName;
  }

  private static @NotNull @NlsSafe String generateProjectName(@NotNull String externalProjectPath) {
    File projectFile = new File(externalProjectPath);
    if (projectFile.isFile()) {
      return projectFile.getParentFile().getName();
    }
    else {
      return projectFile.getName();
    }
  }

  @Override
  protected void setState(@NotNull ExternalSystemTaskState state) {
    super.setState(state);
    if (state.isStopped() &&
        // merging existing cache data with the new partial data is not supported yet
        !(myResolverPolicy != null && myResolverPolicy.isPartialDataResolveAllowed())) {
      InternalExternalProjectInfo projectInfo =
        new InternalExternalProjectInfo(getExternalSystemId(), getExternalProjectPath(), myExternalProject.getAndSet(null));
      final long currentTimeMillis = System.currentTimeMillis();
      projectInfo.setLastImportTimestamp(currentTimeMillis);
      projectInfo.setLastSuccessfulImportTimestamp(state == ExternalSystemTaskState.FAILED ? -1 : currentTimeMillis);
      ProjectDataManagerImpl.getInstance().updateExternalProjectData(getIdeProject(), projectInfo);
    }
  }
}
