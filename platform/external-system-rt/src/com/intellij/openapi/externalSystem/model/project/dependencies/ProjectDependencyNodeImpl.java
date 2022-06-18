// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public String getProjectName() {
    return projectName;
  }

  @NotNull
  @Override
  public String getProjectPath() {
    return projectPath;
  }

  @NonNls
  @NotNull
  @Override
  public String getDisplayName() {
    return "project " + projectName;
  }
}
