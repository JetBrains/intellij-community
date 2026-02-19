// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class ImportSpecImpl implements ImportSpec {

  private final @NotNull Project myProject;
  private final @NotNull ProjectSystemId myExternalSystemId;
  private @NotNull ProgressExecutionMode myProgressExecutionMode;
  private @Nullable ExternalProjectRefreshCallback myCallback;
  private boolean isPreviewMode;
  private boolean importProjectData;
  private boolean selectProjectDataToImport;
  private boolean createDirectoriesForEmptyContentRoots;
  private boolean isActivateBuildToolWindowOnStart;
  private boolean isActivateBuildToolWindowOnFailure;
  private @NotNull ThreeState myNavigateToError;
  private @Nullable String myVmOptions;
  private @Nullable String myArguments;
  private @Nullable ProjectResolverPolicy myProjectResolverPolicy;
  private @Nullable Runnable myRerunAction;
  private @Nullable UserDataHolderBase myUserData;

  /**
   * @deprecated use {@link ImportSpecBuilder} instead
   */
  @Deprecated
  public ImportSpecImpl(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.MODAL_SYNC;
    myNavigateToError = ThreeState.UNSURE;
  }

  public ImportSpecImpl(
    @NotNull Project project,
    @NotNull ProjectSystemId externalSystemId,
    @NotNull ProgressExecutionMode progressExecutionMode,
    @Nullable ExternalProjectRefreshCallback callback,
    boolean isPreviewMode,
    boolean importProjectData,
    boolean selectProjectDataToImport,
    boolean createDirectoriesForEmptyContentRoots,
    boolean isActivateBuildToolWindowOnStart,
    boolean isActivateBuildToolWindowOnFailure,
    @NotNull ThreeState navigateToError,
    @Nullable String vmOptions,
    @Nullable String arguments,
    @Nullable ProjectResolverPolicy projectResolverPolicy,
    @Nullable Runnable rerunAction,
    @Nullable UserDataHolderBase userData
  ) {
    this.myProject = project;
    this.myExternalSystemId = externalSystemId;
    this.myProgressExecutionMode = progressExecutionMode;
    this.myCallback = callback;
    this.isPreviewMode = isPreviewMode;
    this.importProjectData = importProjectData;
    this.selectProjectDataToImport = selectProjectDataToImport;
    this.createDirectoriesForEmptyContentRoots = createDirectoriesForEmptyContentRoots;
    this.isActivateBuildToolWindowOnStart = isActivateBuildToolWindowOnStart;
    this.isActivateBuildToolWindowOnFailure = isActivateBuildToolWindowOnFailure;
    this.myNavigateToError = navigateToError;
    this.myVmOptions = vmOptions;
    this.myArguments = arguments;
    this.myProjectResolverPolicy = projectResolverPolicy;
    this.myRerunAction = rerunAction;
    this.myUserData = userData;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @Override
  public @NotNull ProgressExecutionMode getProgressExecutionMode() {
    return myProgressExecutionMode;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#use} instead
   */
  @Deprecated
  public void setProgressExecutionMode(@NotNull ProgressExecutionMode progressExecutionMode) {
    myProgressExecutionMode = progressExecutionMode;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withCallback} instead
   */
  @Deprecated
  public void setCallback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
  }

  @Override
  public @Nullable ExternalProjectRefreshCallback getCallback() {
    return myCallback;
  }

  @Override
  public boolean isPreviewMode() {
    return isPreviewMode;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withPreviewMode} instead
   */
  @Deprecated
  public void setPreviewMode(boolean isPreviewMode) {
    this.isPreviewMode = isPreviewMode;
  }

  @Override
  public boolean shouldImportProjectData() {
    return importProjectData;
  }

  @Override
  public boolean shouldSelectProjectDataToImport() {
    return selectProjectDataToImport;
  }

  @Override
  public boolean shouldCreateDirectoriesForEmptyContentRoots() {
    return createDirectoriesForEmptyContentRoots;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#createDirectoriesForEmptyContentRoots} instead
   */
  @Deprecated
  public void setCreateDirectoriesForEmptyContentRoots(boolean createDirectoriesForEmptyContentRoots) {
    this.createDirectoriesForEmptyContentRoots = createDirectoriesForEmptyContentRoots;
  }

  @Override
  public boolean isActivateBuildToolWindowOnStart() {
    return isActivateBuildToolWindowOnStart;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withActivateToolWindowOnStart} instead
   */
  @Deprecated
  public void setActivateBuildToolWindowOnStart(boolean isActivate) {
    isActivateBuildToolWindowOnStart = isActivate;
  }

  @Override
  public boolean isActivateBuildToolWindowOnFailure() {
    return isActivateBuildToolWindowOnFailure;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withActivateToolWindowOnFailure} instead
   */
  @Deprecated
  public void setActivateBuildToolWindowOnFailure(boolean isActivate) {
    isActivateBuildToolWindowOnFailure = isActivate;
  }

  @Override
  public @NotNull ThreeState isNavigateToError() {
    return myNavigateToError;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#navigateToError} instead
   */
  @Deprecated
  public void setNavigateToError(@NotNull ThreeState navigateToError) {
    myNavigateToError = navigateToError;
  }

  @Override
  public @Nullable String getVmOptions() {
    return myVmOptions;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withVmOptions} instead
   */
  @Deprecated
  public void setVmOptions(@Nullable String vmOptions) {
    myVmOptions = vmOptions;
  }

  @Override
  public @Nullable String getArguments() {
    return myArguments;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withArguments} instead
   */
  @Deprecated
  public void setArguments(@Nullable String arguments) {
    myArguments = arguments;
  }

  @Override
  public @Nullable ProjectResolverPolicy getProjectResolverPolicy() {
    return myProjectResolverPolicy;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#projectResolverPolicy} instead
   */
  @Deprecated
  void setProjectResolverPolicy(@Nullable ProjectResolverPolicy projectResolverPolicy) {
    myProjectResolverPolicy = projectResolverPolicy;
  }

  @Override
  public @Nullable Runnable getRerunAction() {
    return myRerunAction;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withRerunAction} instead
   */
  @Deprecated
  public void setRerunAction(@Nullable Runnable rerunAction) {
    myRerunAction = rerunAction;
  }

  @Override
  public @Nullable UserDataHolderBase getUserData() {
    return myUserData;
  }

  /**
   * @deprecated use {@link ImportSpecBuilder#withUserData} instead
   */
  @Deprecated
  public void setUserData(@Nullable UserDataHolderBase userData) {
    myUserData = userData;
  }
}
