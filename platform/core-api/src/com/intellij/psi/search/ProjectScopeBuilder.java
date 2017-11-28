/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectScopeBuilder {
  public static ProjectScopeBuilder getInstance(Project project) {
    return ServiceManager.getService(project, ProjectScopeBuilder.class);
  }

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
