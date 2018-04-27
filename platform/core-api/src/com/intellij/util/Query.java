// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author max
 */
public interface Query<Result> extends Iterable<Result> {
  /**
   * Get all of the results in the {@link Collection}
   * @return results in a collection or empty collection if no results found.
   */
  @NotNull
  Collection<Result> findAll();

  /**
   * Get the first result or {@code null} if no results have been found.
   * @return first result of the search or {@code null} if no results.
   */
  @Nullable
  Result findFirst();

  /**
   * Process search results one-by-one. All the results will be subsequently fed to a {@code consumer} passed.
   * @param consumer - a processor search results should be fed to.
   * @return {@code true} if the search was completed normally,
   *         {@code false} if the occurrence processing was cancelled by the processor.
   */
  boolean forEach(@NotNull Processor<? super Result> consumer);

  @NotNull
  AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super Result> consumer);

  @NotNull
  Result[] toArray(@NotNull Result[] a);
}
