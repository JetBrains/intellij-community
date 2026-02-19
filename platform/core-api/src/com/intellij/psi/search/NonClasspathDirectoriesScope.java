// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NonClasspathDirectoriesScope extends GlobalSearchScope {
  private final Set<VirtualFile> myRoots;

  public NonClasspathDirectoriesScope(@NotNull Collection<? extends VirtualFile> roots) {
    myRoots = new HashSet<>(roots);
  }

  public static @NotNull GlobalSearchScope compose(@NotNull List<? extends VirtualFile> roots) {
    if (roots.isEmpty()) {
      return EMPTY_SCOPE;
    }

    return new NonClasspathDirectoriesScope(roots);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return VfsUtilCore.isUnder(file, myRoots);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NonClasspathDirectoriesScope)) return false;

    NonClasspathDirectoriesScope that = (NonClasspathDirectoriesScope)o;

    if (!myRoots.equals(that.myRoots)) return false;

    return true;
  }

  @Override
  public int calcHashCode() {
    return myRoots.hashCode();
  }

  @Override
  public @NotNull String getDisplayName() {
    if (myRoots.size() == 1) {
      VirtualFile root = myRoots.iterator().next();
      return CoreBundle.message("scope.display.name.directory.0", root.getName());
    }
    return CoreBundle.message("scope.display.name.directories.0", StringUtil.join(myRoots, file -> "'" + file.getName() + "'", ", "));
  }
}
