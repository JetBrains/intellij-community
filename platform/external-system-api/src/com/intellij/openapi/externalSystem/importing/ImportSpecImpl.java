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
  private boolean createDirectoriesForEmptyContentRoots;
  private boolean isActivateBuildToolWindowOnStart;
  private boolean isActivateBuildToolWindowOnFailure;
  private @NotNull ThreeState myNavigateToError = ThreeState.UNSURE;
  private @Nullable String myVmOptions;
  private @Nullable String myArguments;
  private @Nullable ProjectResolverPolicy myProjectResolverPolicy;
  private @Nullable Runnable myRerunAction;
  private @Nullable UserDataHolderBase myUserData;

  public ImportSpecImpl(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.MODAL_SYNC;
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

  public void setProgressExecutionMode(@NotNull ProgressExecutionMode progressExecutionMode) {
    myProgressExecutionMode = progressExecutionMode;
  }

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

  public void setPreviewMode(boolean isPreviewMode) {
    this.isPreviewMode = isPreviewMode;
  }

  @Override
  public boolean shouldCreateDirectoriesForEmptyContentRoots() {
    return createDirectoriesForEmptyContentRoots;
  }

  public void setCreateDirectoriesForEmptyContentRoots(boolean createDirectoriesForEmptyContentRoots) {
    this.createDirectoriesForEmptyContentRoots = createDirectoriesForEmptyContentRoots;
  }

  @Override
  public boolean isActivateBuildToolWindowOnStart() {
    return isActivateBuildToolWindowOnStart;
  }

  public void setActivateBuildToolWindowOnStart(boolean isActivate) {
    isActivateBuildToolWindowOnStart = isActivate;
  }

  @Override
  public boolean isActivateBuildToolWindowOnFailure() {
    return isActivateBuildToolWindowOnFailure;
  }

  public void setActivateBuildToolWindowOnFailure(boolean isActivate) {
    isActivateBuildToolWindowOnFailure = isActivate;
  }

  @Override
  public @NotNull ThreeState isNavigateToError() {
    return myNavigateToError;
  }

  public void setNavigateToError(@NotNull ThreeState navigateToError) {
    myNavigateToError = navigateToError;
  }

  @Override
  public @Nullable String getVmOptions() {
    return myVmOptions;
  }

  public void setVmOptions(@Nullable String vmOptions) {
    myVmOptions = vmOptions;
  }

  @Override
  public @Nullable String getArguments() {
    return myArguments;
  }

  public void setArguments(@Nullable String arguments) {
    myArguments = arguments;
  }

  public @Nullable ProjectResolverPolicy getProjectResolverPolicy() {
    return myProjectResolverPolicy;
  }

  void setProjectResolverPolicy(@Nullable ProjectResolverPolicy projectResolverPolicy) {
    myProjectResolverPolicy = projectResolverPolicy;
  }

  public @Nullable Runnable getRerunAction() {
    return myRerunAction;
  }

  public void setRerunAction(@Nullable Runnable rerunAction) {
    myRerunAction = rerunAction;
  }

  @Override
  public @Nullable UserDataHolderBase getUserData() {
    return myUserData;
  }

  public void setUserData(@Nullable UserDataHolderBase userData) {
    myUserData = userData;
  }
}
