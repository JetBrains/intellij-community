// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

@Internal
public interface IndexableFilesIndex {

  static @NotNull IndexableFilesIndex getInstance(@NotNull Project project) {
    return project.getService(IndexableFilesIndex.class);
  }

  @RequiresBackgroundThread
  boolean shouldBeIndexed(@NotNull VirtualFile file);

  /**
   * This method is significantly more expensive than {@link IndexableFilesIndex#shouldBeIndexed(VirtualFile)}
   * Consider using this method if it's enough.
   * <br/>
   * Most of {@link IndexableSetOrigin} contain roots. In this case they contain registered roots of corresponding workspace entity or
   * other indexable unit, like {@link com.intellij.openapi.roots.SyntheticLibrary} or {@link IndexableSetContributor}.
   * Consider structure `contentRoot/dir/file`. {@code getOrigins(file).singleOrError().getRoots()} is `contentRoot`.
   * Why this is important: some similar APIs are written for incremental reindexing, and work with minimal necessary roots. For example,
   * {@link ReincludedRootsUtil}.
   * <br/>
   * Batch handling is introduced for the sake of performance.
   * <br/>
   * Just like `WorkspaceFileIndex`, `IndexableFilesIndex` doesn't detect cases when a file gets under some `IndexableSetOrigin`
   * due to a symlink on it or its parent.
   * In case `rootOfOrigin1/targetFile` and `rootOfOrigin2/symlinkToTargetFile` only `Origin1` would be returned by `getOrigins(targetFile)`.
   * See more in {@link IndexableFilesIndexSymlinkedOriginsTest}
   */
  @RequiresBackgroundThread
  @NotNull
  @Unmodifiable
  Collection<? extends IndexableSetOrigin> getOrigins(@NotNull Collection<VirtualFile> files);

  @RequiresBackgroundThread
  @NotNull
  List<IndexableFilesIterator> getIndexingIterators();

  @RequiresBackgroundThread
  @NotNull
  Collection<IndexableFilesIterator> getModuleIndexingIterators(@NotNull ModuleEntity entity, @NotNull EntityStorage entityStorage);
}