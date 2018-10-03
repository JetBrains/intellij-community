// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

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

  /**
   * Checks whether predicate is satisfied for every result of this query.
   * This operation short-circuits once predicate returns false.
   * Technically it's equivalent to {@link #forEach(Processor)}, but has better name.
   * Use this method only if your predicate is stateless and side-effect free.
   *
   * @param predicate predicate to test on query results
   *
   * @return true if given predicate is satisfied for all query results.
   */
  @Contract(pure = true)
  default boolean allMatch(@NotNull Predicate<? super Result> predicate) {
    return forEach(predicate::test);
  }

  /**
   * Checks whether predicate is satisfied for at least one result of this query.
   * This operation short-circuits once predicate returns true.
   * Use this method only if your predicate is stateless and side-effect free.
   *
   * @param predicate predicate to test on query results
   *
   * @return true if given predicate is satisfied for at least one query result.
   */
  @Contract(pure = true)
  default boolean anyMatch(@NotNull Predicate<? super Result> predicate) {
    return !forEach(t -> !predicate.test(t));
  }
}
