// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ProjectDependenciesImpl implements ProjectDependencies, Serializable {
  private final List<ComponentDependencies> dependencies = new ArrayList<>(0);

  @NotNull
  @Override
  public List<ComponentDependencies> getComponentsDependencies() {
    return dependencies;
  }

  public void add(ComponentDependencies componentDependencies) {
    dependencies.add(componentDependencies);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectDependenciesImpl that = (ProjectDependenciesImpl)o;
    if (!dependencies.equals(that.dependencies)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return dependencies.hashCode();
  }
}
