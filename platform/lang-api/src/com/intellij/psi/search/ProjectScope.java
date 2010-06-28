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

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;

public class ProjectScope {
  private static final Key<GlobalSearchScope> ALL_SCOPE_KEY = new Key<GlobalSearchScope>("ALL_SCOPE_KEY");
  private static final Key<GlobalSearchScope> PROJECT_SCOPE_KEY = new Key<GlobalSearchScope>("PROJECT_SCOPE_KEY");

  private ProjectScope() {
  }

  public static GlobalSearchScope getAllScope(final Project project) {
    GlobalSearchScope allScope = project.getUserData(ALL_SCOPE_KEY);
    if (allScope == null) {
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      allScope = projectRootManager == null ? new EverythingGlobalScope(project) : new ProjectAndLibrariesScope(project);
      allScope = ((UserDataHolderEx)project).putUserDataIfAbsent(ALL_SCOPE_KEY, allScope);
    }
    return allScope;
  }

  public static GlobalSearchScope getProjectScope(final Project project) {
    GlobalSearchScope projectScope = project.getUserData(PROJECT_SCOPE_KEY);
    if (projectScope == null) {
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      if (projectRootManager == null) {
        projectScope = new EverythingGlobalScope(project) {
          public boolean isSearchInLibraries() {
            return false;
          }
        };
      }
      else {
        projectScope = new GlobalSearchScope(project) {
          private final ProjectFileIndex myFileIndex = projectRootManager.getFileIndex();

          public boolean contains(VirtualFile file) {
            if (file instanceof VirtualFileWindow) return true;

            if (myFileIndex.isInLibraryClasses(file) && !myFileIndex.isInSourceContent(file)) return false;

            return myFileIndex.isInContent(file);
          }

          public int compare(VirtualFile file1, VirtualFile file2) {
            return 0;
          }

          public boolean isSearchInModuleContent(@NotNull Module aModule) {
            return true;
          }

          public boolean isSearchInLibraries() {
            return false;
          }

          public String getDisplayName() {
            return PsiBundle.message("psi.search.scope.project");
          }

          public String toString() {
            return getDisplayName();
          }

          @Override
          public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
            if (scope == this || !scope.isSearchInLibraries() || !scope.isSearchOutsideRootModel()) return this;
            return super.uniteWith(scope);
          }

          @NotNull
          @Override
          public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
            if (scope == this) return this;
            if (!scope.isSearchInLibraries()) return scope;
            return super.intersectWith(scope);
          }
        };
      }
      projectScope = ((UserDataHolderEx)project).putUserDataIfAbsent(PROJECT_SCOPE_KEY, projectScope);
    }
    return projectScope;
  }
}
