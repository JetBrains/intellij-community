/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
public interface ImportSpec {

  @NotNull
  Project getProject();

  @NotNull
  ProjectSystemId getExternalSystemId();

  @NotNull
  ProgressExecutionMode getProgressExecutionMode();

  boolean isForceWhenUptodate();

  /**
   * @deprecated see {@link com.intellij.openapi.externalSystem.settings.ExternalProjectSettings#setUseAutoImport} for details
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  default boolean whenAutoImportEnabled() { return false; }

  @Nullable
  ExternalProjectRefreshCallback getCallback();

  boolean isPreviewMode();

  boolean shouldCreateDirectoriesForEmptyContentRoots();

  boolean isReportRefreshError();

  @Nullable
  String getVmOptions();

  @Nullable
  String getArguments();
}
