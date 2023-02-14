// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ProjectScope {
  private static final NotNullLazyKey<GlobalSearchScope, Project> ALL_SCOPE_KEY = NotNullLazyKey.createLazyKey(
    "ALL_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildAllScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> PROJECT_SCOPE_KEY = NotNullLazyKey.createLazyKey(
    "PROJECT_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildProjectScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> LIBRARIES_SCOPE_KEY = NotNullLazyKey.createLazyKey(
    "LIBRARIES_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildLibrariesScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> CONTENT_SCOPE_KEY = NotNullLazyKey.createLazyKey(
    "CONTENT_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildContentScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> EVERYTHING_SCOPE_KEY = NotNullLazyKey.createLazyKey(
    "EVERYTHING_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildEverythingScope());

  private ProjectScope() { }

  /**
   * @return Scope for all things inside the project: files in the project content plus files in libraries/library source
   */
  @NotNull
  public static GlobalSearchScope getAllScope(@NotNull Project project) {
    return ALL_SCOPE_KEY.getValue(project);
  }

  @NotNull
  public static GlobalSearchScope getProjectScope(@NotNull Project project) {
    return PROJECT_SCOPE_KEY.getValue(project);
  }

  @NotNull
  public static GlobalSearchScope getLibrariesScope(@NotNull Project project) {
    return LIBRARIES_SCOPE_KEY.getValue(project);
  }

  @NotNull
  public static GlobalSearchScope getContentScope(@NotNull Project project) {
    return CONTENT_SCOPE_KEY.getValue(project);
  }

  /**
   * @return The biggest possible scope: every file on the planet belongs to this.
   */
  @NotNull
  public static GlobalSearchScope getEverythingScope(@NotNull Project project) {
    return EVERYTHING_SCOPE_KEY.getValue(project);
  }

  public static @NotNull @Nls String getProjectFilesScopeName() {
    return CoreBundle.message("psi.search.scope.project");
  }
}