// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Stores list of external dependencies which are required for project to operate normally. Currently only one kind of dependencies
 * is supported ({@link DependencyOnPlugin}) but in future it may also store required JDKs, application servers and generic path variables.
 */
public abstract class ExternalDependenciesManager {
  public static ExternalDependenciesManager getInstance(@NotNull Project project) {
    return project.getService(ExternalDependenciesManager.class);
  }

  public abstract @Unmodifiable @NotNull <T extends ProjectExternalDependency> List<T> getDependencies(@NotNull Class<T> aClass);

  public abstract @NotNull List<ProjectExternalDependency> getAllDependencies();

  public abstract void setAllDependencies(@NotNull List<? extends ProjectExternalDependency> dependencies);
}
