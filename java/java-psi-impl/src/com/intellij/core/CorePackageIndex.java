// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CorePackageIndex extends PackageIndex {
  private static final Logger LOG = Logger.getInstance(CorePackageIndex.class);

  private final List<VirtualFile> myClasspath = new ArrayList<>();

  public CorePackageIndex() {
  }

  private List<VirtualFile> roots() {
    return myClasspath;
  }

  private List<VirtualFile> findDirectoriesByPackageName(String packageName) {
    List<VirtualFile> result = new ArrayList<>();
    String dirName = packageName.replace(".", "/");
    for (VirtualFile root : roots()) {
      VirtualFile classDir = root.findFileByRelativePath(dirName);
      if (classDir != null) {
        result.add(classDir);
      }
    }
    return result;
  }

  @Override
  public @NotNull Query<VirtualFile> getDirsByPackageName(@NotNull @NlsSafe String packageName, boolean includeLibrarySources) {
    return new CollectionQuery<>(findDirectoriesByPackageName(packageName));
  }

  public void addToClasspath(VirtualFile root) {
    myClasspath.add(root);
  }

  @Override
  public @Nullable String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    for (VirtualFile root : roots()) {
      if (VfsUtilCore.isAncestor(root, dir, false)) {
        return VfsUtilCore.getRelativePath(dir, root, '.');
      }
    }
    return null;
  }
}
