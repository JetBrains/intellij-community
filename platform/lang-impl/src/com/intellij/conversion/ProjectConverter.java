/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.conversion;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class ProjectConverter {

  @Nullable
  public ConversionProcessor<ProjectSettings> createProjectFileConverter() {
    return null;
  }

  @Nullable
  public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
    return null;
  }

  @Nullable
  public ConversionProcessor<WorkspaceSettings> createWorkspaceFileConverter() {
    return null;
  }

  @Nullable
  public ConversionProcessor<RunManagerSettings> createRunConfigurationsConverter() {
    return null;
  }

  @Nullable
  public ConversionProcessor<ProjectLibrariesSettings> createProjectLibrariesConverter() {
    return null;
  }

  @Nullable
  public ConversionProcessor<ArtifactsSettings> createArtifactsConverter() {
    return null;
  }

  public Collection<File> getAdditionalAffectedFiles() {
    return Collections.emptyList();
  }

  public Collection<File> getCreatedFiles() {
    return Collections.emptyList();
  }

  public boolean isConversionNeeded() {
    return false;
  }

  public void preProcessingFinished() throws CannotConvertException {
  }

  public void processingFinished() throws CannotConvertException {
  }

  public void postProcessingFinished() throws CannotConvertException {
  }
}
