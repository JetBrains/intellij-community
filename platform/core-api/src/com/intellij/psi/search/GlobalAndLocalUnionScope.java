// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

class GlobalAndLocalUnionScope extends GlobalSearchScope {
  private final @NotNull LocalSearchScope myLocalScope;
  private final GlobalSearchScope myMyGlobalScope;

  GlobalAndLocalUnionScope(GlobalSearchScope myGlobalScope, @NotNull LocalSearchScope localScope, @NotNull Project project) {
    super(project);
    myLocalScope = localScope;
    myMyGlobalScope = myGlobalScope;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myMyGlobalScope.contains(file) || myLocalScope.isInScope(file);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return myMyGlobalScope.contains(file1) && myMyGlobalScope.contains(file2) ? myMyGlobalScope.compare(file1, file2) : 0;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myMyGlobalScope.isSearchInModuleContent(aModule);
  }

  @Override
  public boolean isSearchInLibraries() {
    return myMyGlobalScope.isSearchInLibraries();
  }

  @Override
  public @Unmodifiable @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return myMyGlobalScope.getUnloadedModulesBelongingToScope();
  }

  @Override
  public @NonNls String toString() {
    return "UnionToLocal: (" + myMyGlobalScope + ", " + myLocalScope + ")";
  }
}
