// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Provides access to various project-level scopes.
 */
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
  public static @NotNull GlobalSearchScope getAllScope(@NotNull Project project) {
    return ALL_SCOPE_KEY.getValue(project);
  }

  /**
   * Returns a scope which is in most of the cases equal to the scope returned by {@link #getContentScope(Project)}. 
   * The only difference is that this scope doesn't include files belonging to the project content, if they are also included in 'classes' 
   * roots of some library and aren't located under a source root of a module.
   * Unless this difference is important for you, please use {@link #getContentScope(Project)}.
   */
  public static @NotNull GlobalSearchScope getProjectScope(@NotNull Project project) {
    return PROJECT_SCOPE_KEY.getValue(project);
  }

  /**
   * Returns a scope containing files from 'binary' and 'source' roots of all libraries and SDK added to the dependencies of the project
   * (for which {@link com.intellij.openapi.roots.ProjectFileIndex#isInLibrary(VirtualFile) isInLibrary} returns {@code true}).
   */
  public static @NotNull GlobalSearchScope getLibrariesScope(@NotNull Project project) {
    return LIBRARIES_SCOPE_KEY.getValue(project);
  }

  /**
   * Returns a scope containing files from content roots of modules and custom project model entities, which are not excluded or ignored
   * (for which {@link com.intellij.openapi.roots.FileIndex#isInContent(VirtualFile) isInContent} returns {@code true}).
   */
  public static @NotNull GlobalSearchScope getContentScope(@NotNull Project project) {
    return CONTENT_SCOPE_KEY.getValue(project);
  }

  /**
   * Returns a scope which contains all files included in {@link #getAllScope(Project) 'all scope'}, and also includes temporary files
   * created in the IDE like scratches, consoles, etc.
   */
  public static @NotNull GlobalSearchScope getEverythingScope(@NotNull Project project) {
    return EVERYTHING_SCOPE_KEY.getValue(project);
  }

  public static @NotNull @Nls String getProjectFilesScopeName() {
    return CoreBundle.message("psi.search.scope.project");
  }
}