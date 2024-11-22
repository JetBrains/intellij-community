// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class NotScope extends DelegatingGlobalSearchScope {
  NotScope(@NotNull GlobalSearchScope scope) {
    super(scope);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return !myBaseScope.contains(file);
  }

  @Override
  public boolean isSearchInLibraries() {
    return true; // not (in library A) is perfectly fine to find classes in another library B.
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return true; // not (some files in module A) is perfectly fine to find classes in another part of module A.
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true; // not (some files in module A) is perfectly fine to find classes in another part of module A.
  }

  @Override
  public String toString() {
    return "NOT: (" + myBaseScope + ")";
  }
}
