// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

public class ProjectScopeImpl extends GlobalSearchScope {
  private final FileIndexFacade myFileIndex;

  public ProjectScopeImpl(@NotNull Project project, @NotNull FileIndexFacade fileIndex) {
    super(project);
    myFileIndex = fileIndex;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (file instanceof ProjectAwareVirtualFile) {
      return ((ProjectAwareVirtualFile)file).isInProject(Objects.requireNonNull(getProject()));
    }
    return myFileIndex.isInProjectScope(file);
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
  public @NotNull String getDisplayName() {
    return ProjectScope.getProjectFilesScopeName();
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  @Override
  public @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return myFileIndex.getUnloadedModuleDescriptions();
  }

  @Override
  public @NotNull GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope == this || !scope.isSearchInLibraries()) return this;
    return super.uniteWith(scope);
  }

  @Override
  public @NotNull GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return this;
    if (!scope.isSearchInLibraries()) return scope;
    return super.intersectWith(scope);
  }
}
