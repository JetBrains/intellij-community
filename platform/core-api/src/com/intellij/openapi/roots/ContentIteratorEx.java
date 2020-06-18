// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface which can be used to receive the contents of a project.
 *
 * @see FileIndex#iterateContent(ContentIterator)
 */
@FunctionalInterface
@ApiStatus.Experimental
public interface ContentIteratorEx extends ContentIterator {
  /**
   * Processes the specified file or directory.
   *
   * @param fileOrDir the file or directory to process.
   * @return <ul>
   *         <li>{@code Status.CONTINUE} if files processing should be continued;</li>
   *         <li>{@code Status.SKIP_CHILDREN} if descendant files of the specified file shouldn't be processed;</li>
   *         <li>{@code Status.STOP} if files processing should be stopped.</li>
   *         </ul>
   */
  @NotNull Status processFileEx(@NotNull VirtualFile fileOrDir);

  @Override
  default boolean processFile(@NotNull VirtualFile fileOrDir) {
    throw new IllegalStateException("Call com.intellij.openapi.roots.ContentIteratorEx#processFileEx instead");
  }

  enum Status {
    CONTINUE, SKIP_CHILDREN, STOP
  }
}
