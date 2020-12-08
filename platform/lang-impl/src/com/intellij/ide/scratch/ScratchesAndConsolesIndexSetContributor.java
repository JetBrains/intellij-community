// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ScratchesAndConsolesIndexSetContributor extends IndexableSetContributor {
  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    return ScratchFileService.getAllRootPaths();
  }
}
