// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Should be used together with {@link IndexableEntityProvider} and {@link IndexableEntityProvider.IndexableIteratorBuilder}
 * to provide indexing policy for a custom workspace model entity.
 * <p>
 * It's possible that such code would also need a custom {@link IndexableFilesIterator} to implement.
 */
@ApiStatus.OverrideOnly
public interface IndexableIteratorBuilderHandler {
  ExtensionPointName<IndexableIteratorBuilderHandler> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableIteratorBuilderHandler");

  boolean accepts(@NotNull IndexableEntityProvider.IndexableIteratorBuilder builder);

  /**
   * This method should do two things:
   * <ul>
   *   <li>filter away doubling {@link IndexableEntityProvider.IndexableIteratorBuilder}s
   *   to avoid double indexing. This functionality depends absolutely on particular builder implementation</li>
   *   <li>instantiate iterators {@link IndexableFilesIterator} which would iterate all files belonging to entities
   *   that produced those builders that should be indexed. If existing iterators are no solution, consider that the usual way
   *   to iterate recursively a VirtualFile is
   *      {@link com.intellij.util.indexing.roots.IndexableFilesIterationMethods#iterateRoots(Project, Iterable, ContentIterator, VirtualFileFilter, boolean)}
   *   </li>
   *
   * </ul>
   */
  @NotNull
  List<? extends IndexableFilesIterator> instantiate(@NotNull Collection<IndexableEntityProvider.IndexableIteratorBuilder> builders,
                                                     @NotNull Project project,
                                                     @NotNull EntityStorage entityStorage);
}
