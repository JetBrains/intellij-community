/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import org.jetbrains.annotations.NotNull;

public class ProjectScope {

  private static final NotNullLazyKey<GlobalSearchScope, Project> ALL_SCOPE_KEY = NotNullLazyKey.create(
    "ALL_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildAllScope());
  private static final NotNullLazyKey<GlobalSearchScope, Project> PROJECT_SCOPE_KEY = NotNullLazyKey.create(
    "PROJECT_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildProjectScope());
  private static final NotNullLazyKey<GlobalSearchScope, Project> LIBRARIES_SCOPE_KEY = NotNullLazyKey.create(
    "LIBRARIES_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildLibrariesScope());
  private static final NotNullLazyKey<GlobalSearchScope, Project> CONTENT_SCOPE_KEY = NotNullLazyKey.create(
    "CONTENT_SCOPE_KEY",
    project -> ProjectScopeBuilder.getInstance(project).buildContentScope());
  private static final NotNullLazyKey<EverythingGlobalScope, Project> EVERYTHING_SCOPE_KEY = NotNullLazyKey.create(
    "EVERYTHING_SCOPE_KEY",
    project -> new EverythingGlobalScope(project) {
      @NotNull
      @Override
      public String getDisplayName() {
        return "All Places";
      }
    });

  private ProjectScope() {
  }

  /**
   * @return Scope for all things inside the project: files in the project content plus files in libraries/libraries sources
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
}
