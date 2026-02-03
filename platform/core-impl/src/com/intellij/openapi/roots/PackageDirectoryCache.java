// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.roots.impl.PackageDirectoryCacheImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Provides a fast way to retrieve information about packages corresponding to nested directories when root directories are given.
 */
public interface PackageDirectoryCache {
  @NotNull List<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName);
  
  @NotNull List<VirtualFile> getFilesByPackageName(@NotNull String packageName);

  @NotNull Set<String> getSubpackageNames(@NotNull String packageName, @NotNull GlobalSearchScope scope);

  static @NotNull PackageDirectoryCache createCache(@NotNull List<? extends VirtualFile> roots) {
    return new PackageDirectoryCacheImpl((packageName, result) -> {
      if (packageName.isEmpty()) {
        PackageDirectoryCacheImpl.addValidDirectories(roots, result);
      }
    }, (dir, name) -> true);
  }
}
