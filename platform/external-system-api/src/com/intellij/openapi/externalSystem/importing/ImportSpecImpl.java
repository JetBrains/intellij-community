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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class ImportSpecImpl implements ImportSpec {
  @NotNull private final Project myProject;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private ProgressExecutionMode myProgressExecutionMode;
  private boolean forceWhenUptodate;
  @Nullable private ExternalProjectRefreshCallback myCallback;
  private boolean isPreviewMode;
  private boolean createDirectoriesForEmptyContentRoots;
  private boolean isReportRefreshError;
  @Nullable private String myVmOptions;
  @Nullable private String myArguments;
  @Nullable private ProjectResolverPolicy myProjectResolverPolicy;

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

  @Override
  public boolean isForceWhenUptodate() {
    return forceWhenUptodate;
  }

  public void setForceWhenUptodate(boolean forceWhenUptodate) {
    this.forceWhenUptodate = forceWhenUptodate;
  }

  /**
   * @deprecated see {@link com.intellij.openapi.externalSystem.settings.ExternalProjectSettings#setUseAutoImport} for details
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public void setWhenAutoImportEnabled(boolean whenAutoImportEnabled) { }

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
  public boolean isReportRefreshError() {
    return isReportRefreshError;
  }

  public void setReportRefreshError(boolean isReportRefreshError) {
    this.isReportRefreshError = isReportRefreshError;
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
}
