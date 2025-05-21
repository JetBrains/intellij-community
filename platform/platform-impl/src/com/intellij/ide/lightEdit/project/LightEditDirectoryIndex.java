// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

final class LightEditDirectoryIndex extends DirectoryIndex {

  @Override
  public @NotNull List<OrderEntry> getOrderEntries(@NotNull VirtualFile fileOrDir) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Set<String> getDependentUnloadedModules(@NotNull Module module) {
    return Collections.emptySet();
  }
}
