// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ProjectDependencyNodeImpl extends AbstractDependencyNode implements ProjectDependencyNode {

  private final String projectName;

  @PropertyMapping({"id", "projectName"}) //NON-NLS
  public ProjectDependencyNodeImpl(long id, @NotNull String projectName) {
    super(id);
    this.projectName = projectName;
  }

  @NotNull
  @Override
  public String getProjectName() {
    return projectName;
  }

  @NonNls
  @NotNull
  @Override
  public String getDisplayName() {
    return "project " + projectName;
  }

  @Override
  public boolean match(AbstractDependencyNode dependencyNode) {
    if (dependencyNode == null || getClass() != dependencyNode.getClass()) return false;
    ProjectDependencyNodeImpl node = (ProjectDependencyNodeImpl)dependencyNode;
    if (!projectName.equals(node.projectName)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + projectName.hashCode();
    return result;
  }
}
