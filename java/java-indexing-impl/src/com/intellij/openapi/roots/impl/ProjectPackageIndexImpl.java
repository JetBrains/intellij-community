// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

final class ProjectPackageIndexImpl extends PackageIndex {
  private final DirectoryIndex myDirectoryIndex;

  ProjectPackageIndexImpl(@NotNull Project project) {
    myDirectoryIndex = DirectoryIndex.getInstance(project);
  }

  @NotNull
  @Override
  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }
}
