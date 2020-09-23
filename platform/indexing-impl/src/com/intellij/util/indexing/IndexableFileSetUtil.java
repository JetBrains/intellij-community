// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class IndexableFileSetUtil {
  public static void iterateIndexableFilesRecursively(@NotNull VirtualFile file,
                                                      @NotNull IndexableFileSet indexableFileSet,
                                                      @NotNull ContentIterator iterator) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {

        if (!indexableFileSet.isInSet(file)) return false;
        iterator.processFile(file);

        return true;
      }
    });
  }
}
