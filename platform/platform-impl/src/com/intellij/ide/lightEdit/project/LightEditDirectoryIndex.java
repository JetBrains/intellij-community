// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

final class LightEditDirectoryIndex extends DirectoryIndex {
  @Override
  public @NotNull Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return EmptyQuery.getEmptyQuery();
  }

  @Override
  public @Nullable String getPackageName(@NotNull VirtualFile fileOrDir) {
    return null;
  }

  @Override
  public @NotNull List<OrderEntry> getOrderEntries(@NotNull VirtualFile fileOrDir) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Set<String> getDependentUnloadedModules(@NotNull Module module) {
    return Collections.emptySet();
  }
}
