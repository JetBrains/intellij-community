// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.ConcurrentBitSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link VirtualFileFilter} used in tandem with {@link IndexableFilesIterator} to skip files that have already been iterated.
 * Several {@link IndexableFilesIterator} might be going to iterate the same roots (for example, if two libraries reference the same .jar file).
 * <br/>
 * Note: even a single {@link IndexableFilesIterator} might potentially have interleaving roots.
 * <br/>
 * Also this class is used to {@link #getNumberOfSkippedFiles() count} files whose iteration has been skipped. This number is used in indexing diagnostics.
 * <br/>
 * This filter is intended to be used in a concurrent environment, where two {@link IndexableFilesIterator iterators} iterate files in different threads.
 */
@ApiStatus.Experimental
public final class IndexableFilesDeduplicateFilter implements VirtualFileFilter {

  private final @Nullable IndexableFilesDeduplicateFilter myDelegate;
  private final ConcurrentBitSet myVisitedFileSet = ConcurrentBitSet.create();
  private final AtomicInteger myNumberOfSkippedFiles = new AtomicInteger();

  private IndexableFilesDeduplicateFilter(@Nullable IndexableFilesDeduplicateFilter delegate) {
    this.myDelegate = delegate;
  }

  /**
   * Create a new filter that counts skipped files from zero.
   */
  public static @NotNull IndexableFilesDeduplicateFilter create() {
    return new IndexableFilesDeduplicateFilter(null);
  }

  /**
   * Create a new filter that counts skipped files from zero and uses the {@code delegate} to determine whether the file should be skipped.
   * <br/>
   * Use case: if there is a "global" filter that skips iterated files across many {@link IndexableFilesIterator},
   * then this method allows to create a delegating filter that counts only files that have been skipped for a specific {@link IndexableFilesIterator}
   */
  public static @NotNull IndexableFilesDeduplicateFilter createDelegatingTo(@NotNull IndexableFilesDeduplicateFilter delegate) {
    if (delegate.myDelegate != null) {
      throw new IllegalStateException("Only one-level delegation is supported now");
    }
    return new IndexableFilesDeduplicateFilter(delegate);
  }

  public int getNumberOfSkippedFiles() {
    return myNumberOfSkippedFiles.get();
  }

  @Override
  public boolean accept(@NotNull VirtualFile file) {
    if (myDelegate != null) {
      boolean shouldVisit = myDelegate.accept(file);
      if (!shouldVisit) {
        myNumberOfSkippedFiles.incrementAndGet();
      }
      return shouldVisit;
    }
    if (file instanceof VirtualFileWithId) {
      int fileId = ((VirtualFileWithId)file).getId();
      if (fileId > 0) {
        boolean wasVisited = myVisitedFileSet.set(fileId);
        if (wasVisited) {
          myNumberOfSkippedFiles.incrementAndGet();
        }
        return !wasVisited;
      }
    }
    myNumberOfSkippedFiles.incrementAndGet();
    return false;
  }
}
