// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Override some of 'create*Converter' methods to perform conversion. If none of these methods suits the needs,
 * override {@link #isConversionNeeded()}, {@link #getAdditionalAffectedFiles()} and one of '*processingFinished' methods
 */
public abstract class ProjectConverter {
  @Nullable
  public ConversionProcessor<ComponentManagerSettings> createProjectFileConverter() {
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

  /**
   * Override this method if conversion affects some configuration files not covered by provided {@link ConversionProcessor}s
   */
  public @NotNull Collection<Path> getAdditionalAffectedFiles() {
    return Collections.emptyList();
  }

  /**
   * @return files created during conversion process
   */
  public @NotNull Collection<Path> getCreatedFiles() {
    return Collections.emptyList();
  }

  /**
   * @return {@code true} if it's required to convert some files not covered by provided {@link ConversionProcessor}s
   */
  public boolean isConversionNeeded() {
    return false;
  }

  /**
   * Performs conversion of files not covered by provided {@link ConversionProcessor}s. Override this method if conversion should be
   * performed before {@link ConversionProcessor#process} for other converters is invoked
   */
  public void preProcessingFinished() throws CannotConvertException {
  }

  /**
   * Performs conversion of files not covered by provided {@link ConversionProcessor}s
   */
  public void processingFinished() throws CannotConvertException {
  }

  /**
   * Performs conversion of files not covered by provided {@link ConversionProcessor}s. Override this method if conversion should be
   * performed after {@link ConversionProcessor#process} for other converters is invoked
   */
  public void postProcessingFinished() throws CannotConvertException {
  }
}
