// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectScopeBuilder {
  public static ProjectScopeBuilder getInstance(Project project) {
    return project.getService(ProjectScopeBuilder.class);
  }

  public abstract @NotNull GlobalSearchScope buildEverythingScope();

  public abstract @NotNull GlobalSearchScope buildLibrariesScope();

  /**
   * @return Scope for all things inside the project: files in the project content plus files in libraries/library source
   */
  public abstract @NotNull GlobalSearchScope buildAllScope();

  public abstract @NotNull GlobalSearchScope buildProjectScope();

  public abstract @NotNull GlobalSearchScope buildContentScope();
}
