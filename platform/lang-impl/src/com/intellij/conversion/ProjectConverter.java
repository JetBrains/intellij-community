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

  public Collection<File> getAdditionalAffectedFiles() {
    return Collections.emptyList();
  }

  public void preProcessingFinished() throws CannotConvertException {
  }

  public void processingFinished() throws CannotConvertException {
  }

  public void postProcessingFinished() throws CannotConvertException {
  }
}
