// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ProjectDependencyNodeImpl extends AbstractDependencyNode implements ProjectDependencyNode {

  private final String projectName;
  private final String projectPath;

  @PropertyMapping({"id", "projectName", "projectPath"}) //NON-NLS
  public ProjectDependencyNodeImpl(long id, @NotNull String projectName, @NotNull String projectPath) {
    super(id);
    this.projectName = projectName;
    this.projectPath = projectPath;
  }

  @Override
  public @NotNull String getProjectName() {
    return projectName;
  }

  @Override
  public @NotNull String getProjectPath() {
    return projectPath;
  }

  @Override
  public @NonNls @NotNull String getDisplayName() {
    return "project " + projectName;
  }
}
