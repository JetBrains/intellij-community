// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class ScratchesAndConsolesIndexSetContributor extends IndexableSetContributor {
  @Override
  public @NotNull Set<VirtualFile> getAdditionalRootsToIndex() {
    return ScratchFileService.getAllRootPaths();
  }

  @Override
  public @NotNull String getDebugName() {
    return "Scratches & Consoles";
  }
}
