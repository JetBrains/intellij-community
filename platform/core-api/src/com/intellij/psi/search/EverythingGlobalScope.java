/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * The biggest possible scope: every file on the planet belongs to this.
 */
public class EverythingGlobalScope extends GlobalSearchScope {
  public EverythingGlobalScope(Project project) {
    super(project);
  }

  public EverythingGlobalScope() {
  }

  @Override
  public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
    return 0;
  }

  @Override
  public boolean contains(@NotNull final VirtualFile file) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  @Override
  public boolean isForceSearchingInLibrarySources() {
    return true;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return true;
  }

  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Project project = getProject();
    return project != null ? FileIndexFacade.getInstance(project).getUnloadedModuleDescriptions() : Collections.emptySet();
  }

  @NotNull
  @Override
  public GlobalSearchScope union(@NotNull SearchScope scope) {
    return this;
  }

  @NotNull
  @Override
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    return scope2;
  }
}