// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModulePackageIndexImpl extends ModulePackageIndex {
  private static final Logger LOG = Logger.getInstance(ModulePackageIndexImpl.class);
  private final ModuleFileIndex myModuleFileIndex;
  private final DirectoryIndex myDirectoryIndex;

  public ModulePackageIndexImpl(@NotNull Module module) {
    myModuleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    myDirectoryIndex = DirectoryIndex.getInstance(module.getProject());
  }

  private final Condition<VirtualFile> myDirCondition = new Condition<>() {
    @Override
    public boolean value(final VirtualFile dir) {
      return dir.isValid() && myModuleFileIndex.getOrderEntryForFile(dir) != null;
    }
  };

  @Override
  public @NotNull Query<VirtualFile> getDirsByPackageName(@NotNull @NlsSafe String packageName, boolean includeLibrarySources) {
    return new FilteredQuery<>(myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources), myDirCondition);
  }

  @Override
  public Query<VirtualFile> getFilesByPackageName(@NotNull String packageName) {
    return new FilteredQuery<>(myDirectoryIndex.getFilesByPackageName(packageName), myDirCondition);
  }

  @Override
  public @Nullable String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    return myDirectoryIndex.getPackageName(dir);
  }
}
