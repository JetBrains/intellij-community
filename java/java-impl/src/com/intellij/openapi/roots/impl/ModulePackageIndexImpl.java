// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class ModulePackageIndexImpl extends ModulePackageIndex {
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

  @NotNull
  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return new FilteredQuery<>(myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources), myDirCondition);
  }

  @Override
  public VirtualFile @NotNull [] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }
}
