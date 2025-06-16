// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProjectPackageIndexImpl extends PackageIndex {
  private static final Logger LOG = Logger.getInstance(ProjectPackageIndexImpl.class);
  
  private final WorkspaceFileIndexEx myWorkspaceFileIndex;

  @ApiStatus.Internal
  public ProjectPackageIndexImpl(@NotNull Project project) {
    myWorkspaceFileIndex = WorkspaceFileIndexEx.getInstance(project);
  }

  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull @NlsSafe String packageName,
                                                 @NotNull GlobalSearchScope scope) {
    return myWorkspaceFileIndex.getDirectoriesByPackageName(packageName, scope);
  }

  @Override
  public Query<VirtualFile> getFilesByPackageName(@NotNull String packageName) {
    return myWorkspaceFileIndex.getFilesByPackageName(packageName);
  }

  @Override
  public @NotNull Query<VirtualFile> getDirsByPackageName(@NotNull @NlsSafe String packageName, boolean includeLibrarySources) {
    return myWorkspaceFileIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @Override
  public @Nullable String getPackageName(@NotNull VirtualFile fileOrDir) {
    return myWorkspaceFileIndex.getPackageName(fileOrDir);
  }

  @Override
  public @Nullable String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    if (!dir.isDirectory()) {
      LOG.error(dir.getPresentableUrl() + " is not a directory");
    }
    return myWorkspaceFileIndex.getPackageName(dir);
  }
}
