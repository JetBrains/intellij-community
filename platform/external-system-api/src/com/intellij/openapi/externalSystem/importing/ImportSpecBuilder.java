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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 5/29/2014
 */
public class ImportSpecBuilder {

  @NotNull private final Project myProject;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private ProgressExecutionMode myProgressExecutionMode;
  private boolean myForceWhenUptodate;
  private boolean myWhenAutoImportEnabled;
  @Nullable private ExternalProjectRefreshCallback myCallback;
  private boolean isPreviewMode;
  private boolean isReportRefreshError = true;
  @Nullable private String myVmOptions;
  @Nullable private String myArguments;
  private boolean myUseDefaultCallback;
  private boolean myCreateDirectoriesForEmptyContentRoots;

  public ImportSpecBuilder(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC;
  }

  public ImportSpecBuilder(ImportSpec importSpec) {
    this(importSpec.getProject(), importSpec.getExternalSystemId());
    apply(importSpec);
  }

  public ImportSpecBuilder whenAutoImportEnabled() {
    myWhenAutoImportEnabled = true;
    return this;
  }

  public ImportSpecBuilder use(@NotNull ProgressExecutionMode executionMode) {
    myProgressExecutionMode = executionMode;
    return this;
  }

  public ImportSpecBuilder forceWhenUptodate() {
    return forceWhenUptodate(true);
  }

  public ImportSpecBuilder forceWhenUptodate(boolean force) {
    myForceWhenUptodate = force;
    return this;
  }

  public ImportSpecBuilder callback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
    return this;
  }

  public ImportSpecBuilder usePreviewMode() {
    isPreviewMode = true;
    return this;
  }

  public ImportSpecBuilder createDirectoriesForEmptyContentRoots() {
    myCreateDirectoriesForEmptyContentRoots = true;
    return this;
  }

  public ImportSpecBuilder dontReportRefreshErrors() {
    isReportRefreshError = false;
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

  public ImportSpecBuilder useDefaultCallback() {
    myUseDefaultCallback = true;
    return this;
  }

  public ImportSpec build() {
    ImportSpecImpl mySpec = new ImportSpecImpl(myProject, myExternalSystemId);
    mySpec.setWhenAutoImportEnabled(myWhenAutoImportEnabled);
    mySpec.setProgressExecutionMode(myProgressExecutionMode);
    mySpec.setForceWhenUptodate(myForceWhenUptodate);
    mySpec.setCreateDirectoriesForEmptyContentRoots(myCreateDirectoriesForEmptyContentRoots);
    if (myUseDefaultCallback) {
      mySpec.setCallback(new ExternalProjectRefreshCallback() {
        @Override
        public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
          if (externalProject == null) {
            return;
          }
          final boolean synchronous = mySpec.getProgressExecutionMode() == ProgressExecutionMode.MODAL_SYNC;
          ServiceManager.getService(ProjectDataManager.class).importData(externalProject, mySpec.getProject(), synchronous);
        }
      });
    }
    else {
      mySpec.setCallback(myCallback);
    }
    mySpec.setPreviewMode(isPreviewMode);
    mySpec.setReportRefreshError(isReportRefreshError);
    mySpec.setArguments(myArguments);
    mySpec.setVmOptions(myVmOptions);
    return mySpec;
  }

  private void apply(ImportSpec spec) {
    myWhenAutoImportEnabled = spec.whenAutoImportEnabled();
    myProgressExecutionMode = spec.getProgressExecutionMode();
    myForceWhenUptodate = spec.isForceWhenUptodate();
    myCreateDirectoriesForEmptyContentRoots = spec.shouldCreateDirectoriesForEmptyContentRoots();
    myCallback = spec.getCallback();
    isPreviewMode = spec.isPreviewMode();
    isReportRefreshError = spec.isReportRefreshError();
    myArguments = spec.getArguments();
    myVmOptions = spec.getVmOptions();
  }
}
