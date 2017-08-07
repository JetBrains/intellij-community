/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Scope for all things inside the project: files in the project content plus files in libraries/libraries sources
 */
public class ProjectAndLibrariesScope extends GlobalSearchScope {
  protected final ProjectFileIndex myProjectFileIndex;
  protected final boolean mySearchOutsideRootModel;
  private String myDisplayName = PsiBundle.message("psi.search.scope.project.and.libraries");

  public ProjectAndLibrariesScope(Project project) {
    this(project, false);
  }

  public ProjectAndLibrariesScope(Project project, boolean searchOutsideRootModel) {
    super(project);
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    mySearchOutsideRootModel = searchOutsideRootModel;
  }

  public boolean contains(@NotNull VirtualFile file) {
    return myProjectFileIndex.isInContent(file) ||
           myProjectFileIndex.isInLibraryClasses(file) ||
           myProjectFileIndex.isInLibrarySource(file);
  }

  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    List<OrderEntry> entries1 = myProjectFileIndex.getOrderEntriesForFile(file1);
    List<OrderEntry> entries2 = myProjectFileIndex.getOrderEntriesForFile(file2);
    if (entries1.size() != entries2.size()) return 0;

    int res = 0;
    for (OrderEntry entry1 : entries1) {
      Module module = entry1.getOwnerModule();
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
      OrderEntry entry2 = moduleFileIndex.getOrderEntryForFile(file2);
      if (entry2 == null) {
        return 0;
      }
      else {
        int aRes = entry2.compareTo(entry1);
        if (aRes == 0) return 0;
        if (res == 0) {
          res = aRes;
        }
        else if (res != aRes) {
          return 0;
        }
      }
    }

    return res;
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return mySearchOutsideRootModel;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  public boolean isSearchInLibraries() {
    return true;
  }

  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    Project project = getProject();
    return project != null ? ModuleManager.getInstance(project).getUnloadedModuleDescriptions() : Collections.emptySet();
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  public void setDisplayName(@NotNull String displayName) {
    myDisplayName = displayName;
  }

  @NotNull
  public GlobalSearchScope intersectWith(@NotNull final GlobalSearchScope scope) {
    if (scope.isSearchOutsideRootModel()) {
      return super.intersectWith(scope);
    }

    return scope;
  }

  @NotNull
  public GlobalSearchScope uniteWith(@NotNull final GlobalSearchScope scope) {
    if (scope.isSearchOutsideRootModel()) {
      return super.uniteWith(scope);
    }

    return this;
  }

  public String toString() {
    return getDisplayName();
  }
}
