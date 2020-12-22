// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
   * Iterates through all files and directories corresponding to this iterator.
   * <br />
   * The {@code indexableFilesDeduplicateFilter} is used to not visit files twice
   * when several {@code IndexableFilesIterator}-s would iterate the same roots (probably in different threads).
   * <br />
   * The {@code fileIterator} should be invoked on every new file (with respect to {@code indexableFilesDeduplicateFilter}).
   * If the {@code fileIterator} returns false, the iteration should be stopped and this method should return {@code false}.
   *
   * @return {@code false} if the {@code fileIterator} has stopped the iteration by returning {@code false}, {@code true} otherwise.
   */
  boolean iterateFiles(@NotNull Project project,
                       @NotNull ContentIterator fileIterator,
                       @NotNull IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter);
}
