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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 5/29/2014
 */
public class ImportSpec {
  @NotNull private final Project myProject;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private ProgressExecutionMode myProgressExecutionMode;
  private boolean forceWhenUptodate;
  private boolean whenAutoImportEnabled;
  @Nullable private ExternalProjectRefreshCallback myCallback;
  //private boolean isPreviewMode;
  //private boolean isReportRefreshError;

  public ImportSpec(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.MODAL_SYNC;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @NotNull
  public ProgressExecutionMode getProgressExecutionMode() {
    return myProgressExecutionMode;
  }

  public void setProgressExecutionMode(@NotNull ProgressExecutionMode progressExecutionMode) {
    myProgressExecutionMode = progressExecutionMode;
  }

  public boolean isForceWhenUptodate() {
    return forceWhenUptodate;
  }

  public void setForceWhenUptodate(boolean forceWhenUptodate) {
    this.forceWhenUptodate = forceWhenUptodate;
  }

  public boolean isWhenAutoImportEnabled() {
    return whenAutoImportEnabled;
  }

  public void setWhenAutoImportEnabled(boolean whenAutoImportEnabled) {
    this.whenAutoImportEnabled = whenAutoImportEnabled;
  }

  public void setCallback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
  }

  @Nullable
  public ExternalProjectRefreshCallback getCallback() {
    return myCallback;
  }

  //public boolean isPreviewMode() {
  //  return isPreviewMode;
  //}
  //
  //public void setPreviewMode(boolean isPreviewMode) {
  //  this.isPreviewMode = isPreviewMode;
  //}
  //
  //public boolean isReportRefreshError() {
  //  return isReportRefreshError;
  //}
  //
  //public void setReportRefreshError(boolean isReportRefreshError) {
  //  this.isReportRefreshError = isReportRefreshError;
  //}
}
