// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.*;

import java.util.Set;

/**
 * Provides a named file set to be indexed for a single project structure entity (module, library, SDK, etc.)
 * Allows the indexing infrastructure to prioritize indexing by some predicate.
 *
 * @see ModuleIndexableFilesIterator
 * @see LibraryIndexableFilesIterator
 * @see SyntheticLibraryIndexableFilesIteratorImpl
 * @see SdkIndexableFilesIteratorImpl
 * @see IndexableSetContributorFilesIterator
 */
@Debug.Renderer(text = "getClass().getName() + \":\" + getDebugName()")
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
public interface IndexableFilesIterator {

  /**
   * Presentable name that can be shown in logs and used for debugging purposes.
   */
  @NonNls
  String getDebugName();

  /**
   * Presentable text shown in progress indicator during indexing of files of this provider.
   */
  @NlsContexts.ProgressText
  String getIndexingProgressText();

  /**
   * Presentable text shown in progress indicator during traversing of files of this provider.
   */
  @NlsContexts.ProgressText
  String getRootsScanningProgressText();

  /**
   * Represents origins (module, library, etc) of indexable file iterator.
   * All instances of origin from different invocations of this method on the same {@link IndexableFilesIterator} are expected to be equal.
   * <p>
   * Iterators with equal origins would be considered duplicates, and only one would be actually used during indexing.
   * Unique origins may result in performance degradation, since multiple equal iterators might result in batch changes
   * like complex workspace change.
   * <p>
   * Consider implementing suitable interface from indexableSetOriginsApi.kt. Those interfaces are used to distinguish sources of files.
   * Especially use {@link com.intellij.util.indexing.roots.kind.ContentOrigin} when files to be indexed
   * belong to the project content. By default, instances of {@link com.intellij.openapi.roots.impl.FilePropertyPusher} are applied on files
   * from {@link com.intellij.util.indexing.roots.kind.ModuleContentOrigin} only.
   */
  @NotNull
  IndexableSetOrigin getOrigin();

  /**
   * Iterates through all files and directories corresponding to this iterator.
   * <br />
   * The {@code fileFilter} is used to not process some files.
   * <br />
   * It is common to pass {@link IndexableFilesDeduplicateFilter} as the {@code fileFilter}
   * to avoid processing the same files twice. Several {@code IndexableFilesIterator}-s
   * may iterate the same roots (probably in different threads).
   *
   * @return {@code false} if the {@code fileIterator} has stopped the iteration by returning {@code false}, {@code true} otherwise.
   */
  boolean iterateFiles(@NotNull Project project,
                       @NotNull ContentIterator fileIterator,
                       @NotNull VirtualFileFilter fileFilter);

  @NotNull @Unmodifiable
  Set<String> getRootUrls(@NotNull Project project);
}
