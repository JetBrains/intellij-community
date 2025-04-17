// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProjectPackageIndexImpl extends PackageIndex {
  private static final Logger LOG = Logger.getInstance(ProjectPackageIndexImpl.class);
  private final DirectoryIndex myDirectoryIndex;

  @ApiStatus.Internal
  public ProjectPackageIndexImpl(@NotNull Project project) {
    myDirectoryIndex = DirectoryIndex.getInstance(project);
  }

  @Override
  public VirtualFile @NotNull [] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName,
                                                 @NotNull GlobalSearchScope scope) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, scope);
  }

  @Override
  public Query<VirtualFile> getFilesByPackageName(@NotNull String packageName) {
    return myDirectoryIndex.getFilesByPackageName(packageName);
  }

  @Override
  public @NotNull Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @Override
  public @Nullable String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    return myDirectoryIndex.getPackageName(dir);
  }
}
