// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Use {@link IndexingIteratorsProvider IndexingIteratorsProvider}
 */
@Internal
@Deprecated(forRemoval = true)
public interface IndexableFilesIndex {

  static @NotNull IndexableFilesIndex getInstance(@NotNull Project project) {
    return project.getService(IndexableFilesIndex.class);
  }

  @RequiresBackgroundThread
  boolean shouldBeIndexed(@NotNull VirtualFile file);

  @RequiresBackgroundThread
  @NotNull
  List<IndexableFilesIterator> getIndexingIterators();
}