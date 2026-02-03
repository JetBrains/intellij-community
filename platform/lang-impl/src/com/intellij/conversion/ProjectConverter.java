// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Override some of 'create*Converter' methods and return instance of this class from {@link ConverterProvider#createConverter} to perform
 * conversion. If none of these methods suits the needs, override {@link #isConversionNeeded()},
 * {@link #getAdditionalAffectedFiles()} and one of '*processingFinished' methods.
 * <p>Conversion is performed in 4 phases. Firstly, it's determined whether conversion {@link ConversionProcessor#isConversionNeeded is needed}.
 * Then {@link ConversionProcessor#preProcess pre-processing} is invoked for each affected converter, after that {@link ConversionProcessor#process} processing}
 * is invoked for all these converters, and finally {@link ConversionProcessor#postProcess} post-processing} is performed.
 * </p>
 */
public abstract class ProjectConverter {
  public @Nullable ConversionProcessor<ComponentManagerSettings> createProjectFileConverter() {
    return null;
  }

  public @Nullable ConversionProcessor<ModuleSettings> createModuleFileConverter() {
    return null;
  }

  public @Nullable ConversionProcessor<WorkspaceSettings> createWorkspaceFileConverter() {
    return null;
  }

  public @Nullable ConversionProcessor<RunManagerSettings> createRunConfigurationsConverter() {
    return null;
  }

  public @Nullable ConversionProcessor<ProjectLibrariesSettings> createProjectLibrariesConverter() {
    return null;
  }

  public @Nullable ConversionProcessor<ArtifactsSettings> createArtifactsConverter() {
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
