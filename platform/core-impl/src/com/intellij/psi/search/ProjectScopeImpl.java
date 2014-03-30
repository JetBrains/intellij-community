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
package com.intellij.psi.search;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NotNull;

public class ProjectScopeImpl extends GlobalSearchScope {
  private final FileIndexFacade myFileIndex;

  public ProjectScopeImpl(Project project, FileIndexFacade fileIndex) {
    super(project);
    myFileIndex = fileIndex;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) return true;

    if (myFileIndex.isInLibraryClasses(file) && !myFileIndex.isInSourceContent(file)) return false;

    return myFileIndex.isInContent(file);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return 0;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public String getDisplayName() {
    return PsiBundle.message("psi.search.scope.project");
  }

  public String toString() {
    return getDisplayName();
  }

  @NotNull
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
}
