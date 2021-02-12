// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.DeprecatedMethodException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IndexableFileSet {
  boolean isInSet(@NotNull VirtualFile file);

  /**
   * @deprecated method is not used anymore.
   * We directly traverse file with {@link VfsUtilCore#visitChildrenRecursively(VirtualFile, VirtualFileVisitor)} now.
   */
  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  default void iterateIndexableFilesIn(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
    DeprecatedMethodException.report("Unsupported method is not used anymore");
  }
}