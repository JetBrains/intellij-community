// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectScopeBuilder {
  public static ProjectScopeBuilder getInstance(Project project) {
    return project.getService(ProjectScopeBuilder.class);
  }

  @NotNull
  public abstract GlobalSearchScope buildEverythingScope();

  @NotNull
  public abstract GlobalSearchScope buildLibrariesScope();

  /**
   * @return Scope for all things inside the project: files in the project content plus files in libraries/libraries sources
   */
  @NotNull
  public abstract GlobalSearchScope buildAllScope();

  @NotNull
  public abstract GlobalSearchScope buildProjectScope();

  @NotNull
  public abstract GlobalSearchScope buildContentScope();
}
