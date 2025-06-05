// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class ImportSpecBuilder {

  private final @NotNull Project myProject;
  private final @NotNull ProjectSystemId myExternalSystemId;
  private @NotNull ProgressExecutionMode myProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC;
  private @Nullable ExternalProjectRefreshCallback myCallback = null;
  private boolean isPreviewMode = false;
  private boolean isActivateBuildToolWindowOnStart = false;
  private boolean isActivateBuildToolWindowOnFailure = true;
  private @NotNull ThreeState isNavigateToError = ThreeState.UNSURE;
  private @Nullable String myVmOptions = null;
  private @Nullable String myArguments = null;
  private boolean myCreateDirectoriesForEmptyContentRoots = false;
  private @Nullable ProjectResolverPolicy myProjectResolverPolicy = null;
  private @Nullable Runnable myRerunAction = null;
  private @Nullable UserDataHolderBase myUserData = null;

  public ImportSpecBuilder(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
  }

  public ImportSpecBuilder(@NotNull ImportSpec importSpec) {
    myProject = importSpec.getProject();
    myExternalSystemId = importSpec.getExternalSystemId();
    myProgressExecutionMode = importSpec.getProgressExecutionMode();
    myCallback = importSpec.getCallback();
    isPreviewMode = importSpec.isPreviewMode();
    isActivateBuildToolWindowOnStart = importSpec.isActivateBuildToolWindowOnStart();
    isActivateBuildToolWindowOnFailure = importSpec.isActivateBuildToolWindowOnFailure();
    isNavigateToError = importSpec.isNavigateToError();
    myVmOptions = importSpec.getVmOptions();
    myArguments = importSpec.getArguments();
    myCreateDirectoriesForEmptyContentRoots = importSpec.shouldCreateDirectoriesForEmptyContentRoots();
    myProjectResolverPolicy = importSpec.getProjectResolverPolicy();
    myRerunAction = importSpec.getRerunAction();
    myUserData = importSpec.getUserData();
  }

  public ImportSpecBuilder use(@NotNull ProgressExecutionMode executionMode) {
    myProgressExecutionMode = executionMode;
    return this;
  }

  /**
   * @deprecated it does nothing from
   * 16.02.2017, 16:42, ebef09cdbbd6ace3c79d3e4fb63028bac2f15f75
   */
  @Deprecated(forRemoval = true)
  public ImportSpecBuilder forceWhenUptodate(boolean force) {
    return this;
  }

  public ImportSpecBuilder callback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
    return this;
  }

  public ImportSpecBuilder usePreviewMode() {
    return withPreviewMode(true);
  }

  @CheckReturnValue
  public ImportSpecBuilder withPreviewMode(boolean isPreviewMode) {
    this.isPreviewMode = isPreviewMode;
    return this;
  }

  public ImportSpecBuilder createDirectoriesForEmptyContentRoots() {
    myCreateDirectoriesForEmptyContentRoots = true;
    return this;
  }

  @CheckReturnValue
  public ImportSpecBuilder withActivateToolWindowOnStart(boolean activateToolWindowBeforeRun) {
    isActivateBuildToolWindowOnStart = activateToolWindowBeforeRun;
    return this;
  }

  public ImportSpecBuilder activateBuildToolWindowOnStart() {
    return withActivateToolWindowOnStart(true);
  }

  @CheckReturnValue
  public ImportSpecBuilder withActivateToolWindowOnFailure(boolean activateToolWindowOnFailure) {
    isActivateBuildToolWindowOnFailure = activateToolWindowOnFailure;
    return this;
  }

  public ImportSpecBuilder dontReportRefreshErrors() {
    return withActivateToolWindowOnFailure(false);
  }

  public ImportSpecBuilder dontNavigateToError() {
    isNavigateToError = ThreeState.NO;
    return this;
  }

  public ImportSpecBuilder navigateToError() {
    isNavigateToError = ThreeState.YES;
    return this;
  }

  public ImportSpecBuilder withVmOptions(@Nullable String vmOptions) {
    myVmOptions = vmOptions;
    return this;
  }

  public ImportSpecBuilder withArguments(@Nullable String arguments) {
    myArguments = arguments;
    return this;
  }

  @ApiStatus.Experimental
  public ImportSpecBuilder projectResolverPolicy(@NotNull ProjectResolverPolicy projectResolverPolicy) {
    myProjectResolverPolicy = projectResolverPolicy;
    return this;
  }

  public ImportSpecBuilder withRerunAction(@Nullable Runnable rerunAction) {
    myRerunAction = rerunAction;
    return this;
  }

  public ImportSpecBuilder withUserData(@Nullable UserDataHolderBase userData) {
    myUserData = userData;
    return this;
  }

  public ImportSpec build() {
    return new ImportSpecImpl(
      myProject,
      myExternalSystemId,
      myProgressExecutionMode,
      myCallback,
      isPreviewMode,
      myCreateDirectoriesForEmptyContentRoots,
      isActivateBuildToolWindowOnStart,
      isActivateBuildToolWindowOnFailure,
      isNavigateToError,
      myVmOptions,
      myArguments,
      myProjectResolverPolicy,
      myRerunAction,
      myUserData
    );
  }

  @ApiStatus.Internal
  public static final class DefaultProjectRefreshCallback implements ExternalProjectRefreshCallback {
    private final Project myProject;

    public DefaultProjectRefreshCallback(ImportSpec spec) {
      myProject = spec.getProject();
    }

    @Override
    public void onSuccess(final @Nullable DataNode<ProjectData> externalProject) {
      if (externalProject == null) {
        return;
      }
      ProjectDataManager.getInstance().importData(externalProject, myProject);
    }
  }
}
