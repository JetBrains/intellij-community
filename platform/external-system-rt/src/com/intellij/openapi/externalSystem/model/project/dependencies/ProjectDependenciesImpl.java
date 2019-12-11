// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ProjectDependenciesImpl implements ProjectDependencies, Serializable {
  private final List<ComponentDependencies> dependencies = new ArrayList<ComponentDependencies>(0);

  @NotNull
  @Override
  public List<ComponentDependencies> getComponentsDependencies() {
    return dependencies;
  }

  public void add(ComponentDependencies componentDependencies) {
    dependencies.add(componentDependencies);
  }
}
