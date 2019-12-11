// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class ComponentDependenciesImpl implements ComponentDependencies, Serializable {

  private final String componentName;
  private final DependencyScopeNode compileDependencies;
  private final DependencyScopeNode runtimeDependencies;

  @PropertyMapping({"componentName", "compileDependencies", "runtimeDependencies"})
  public ComponentDependenciesImpl(@NotNull String componentName,
                                   @NotNull DependencyScopeNode compileDependencies,
                                   @NotNull DependencyScopeNode runtimeDependencies) {
    this.componentName = componentName;
    this.compileDependencies = compileDependencies;
    this.runtimeDependencies = runtimeDependencies;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return componentName;
  }

  @NotNull
  @Override
  public DependencyScopeNode getCompileDependenciesGraph() {
    return compileDependencies;
  }

  @NotNull
  @Override
  public DependencyScopeNode getRuntimeDependenciesGraph() {
    return runtimeDependencies;
  }
}
