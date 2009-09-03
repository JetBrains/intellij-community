package com.intellij.conversion;

import org.jetbrains.annotations.Nullable;

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

  public void postProcess() {
  } 
}
