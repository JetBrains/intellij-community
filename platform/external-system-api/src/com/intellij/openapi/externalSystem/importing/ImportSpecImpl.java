/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  @NotNull private final Project myProject;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private ProgressExecutionMode myProgressExecutionMode;
  @Nullable private ExternalProjectRefreshCallback myCallback;
  private boolean isPreviewMode;
  private boolean createDirectoriesForEmptyContentRoots;
  private boolean isActivateBuildToolWindowOnStart;
  private boolean isActivateBuildToolWindowOnFailure;
  @NotNull private ThreeState myNavigateToError = ThreeState.UNSURE;
  @Nullable private String myVmOptions;
  @Nullable private String myArguments;
  @Nullable private ProjectResolverPolicy myProjectResolverPolicy;
  @Nullable private Runnable myRerunAction;
  @Nullable private UserDataHolderBase myUserData;

  public ImportSpecImpl(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.MODAL_SYNC;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @NotNull
  @Override
  public ProgressExecutionMode getProgressExecutionMode() {
    return myProgressExecutionMode;
  }

  public void setProgressExecutionMode(@NotNull ProgressExecutionMode progressExecutionMode) {
    myProgressExecutionMode = progressExecutionMode;
  }

  public void setCallback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
  }

  @Nullable
  @Override
  public ExternalProjectRefreshCallback getCallback() {
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

  @Nullable
  @Override
  public String getVmOptions() {
    return myVmOptions;
  }

  public void setVmOptions(@Nullable String vmOptions) {
    myVmOptions = vmOptions;
  }

  @Nullable
  @Override
  public String getArguments() {
    return myArguments;
  }

  public void setArguments(@Nullable String arguments) {
    myArguments = arguments;
  }

  @Nullable
  public ProjectResolverPolicy getProjectResolverPolicy() {
    return myProjectResolverPolicy;
  }

  void setProjectResolverPolicy(@Nullable ProjectResolverPolicy projectResolverPolicy) {
    myProjectResolverPolicy = projectResolverPolicy;
  }

  @Nullable
  public Runnable getRerunAction() {
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
