package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProjectPackageIndexImpl extends ProjectPackageIndexBase {
  @ApiStatus.Internal
  public ProjectPackageIndexImpl(@NotNull Project project) {
    super(project);
  }
}