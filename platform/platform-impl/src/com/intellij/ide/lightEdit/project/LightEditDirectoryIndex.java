// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class LightEditDirectoryIndex extends DirectoryIndex {

  @SuppressWarnings("removal")
  @Override
  public @NotNull DirectoryInfo getInfoForFile(@NotNull VirtualFile file) {
    return getFileInfo();
  }

  @SuppressWarnings("removal")
  static DirectoryInfo getFileInfo() {
    return LightEditDirectoryInfo.INSTANCE;
  }

  @NotNull
  @Override
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return EmptyQuery.getEmptyQuery();
  }

  @Nullable
  @Override
  public String getPackageName(@NotNull VirtualFile dir) {
    return null;
  }

  @NotNull
  @Override
  public List<OrderEntry> getOrderEntries(@NotNull VirtualFile fileOrDir) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Set<String> getDependentUnloadedModules(@NotNull Module module) {
    return Collections.emptySet();
  }

  @SuppressWarnings("removal")
  private static class LightEditDirectoryInfo extends DirectoryInfo {
    private static final LightEditDirectoryInfo INSTANCE = new LightEditDirectoryInfo();
    @Override
    public VirtualFile getContentRoot() {
      return null;
    }
  }
}
