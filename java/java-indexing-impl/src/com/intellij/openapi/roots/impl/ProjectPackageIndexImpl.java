// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ProjectPackageIndexImpl extends PackageIndex {
  private static final Logger LOG = Logger.getInstance(ProjectPackageIndexImpl.class);
  private final DirectoryIndex myDirectoryIndex;

  ProjectPackageIndexImpl(@NotNull Project project) {
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

  @NotNull
  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @Override
  public @Nullable String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    if (!dir.isDirectory()) {
      LOG.error(dir.getPresentableUrl() + " is not a directory");
    }
    return myDirectoryIndex.getPackageName(dir);
  }
}
