// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;
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
   * The consumer might be called on different threads, but by default these calls are mutually exclusive, so no additional
   * synchronization inside consumer is necessary. If you need to process results in parallel, run {@code forEach()} on
   * the result of {@link #allowParallelProcessing()}.
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

  /**
   * @param mapper pure function
   */
  @Experimental
  @NotNull
  default <R> Query<R> mapping(@NotNull Function<? super Result, ? extends R> mapper) {
    return Queries.getInstance().mapping(this, mapper);
  }

  /**
   * @param predicate pure function
   */
  @Experimental
  @NotNull
  default Query<Result> filtering(@NotNull Predicate<? super Result> predicate) {
    return Queries.getInstance().filtering(this, predicate);
  }

  /**
   * @param mapper pure function
   */
  @Experimental
  @NotNull
  default <R> Query<R> flatMapping(@NotNull Function<? super Result, ? extends Query<? extends R>> mapper) {
    return Queries.getInstance().flatMapping(this, mapper);
  }

  /**
   * @return an equivalent query whose {@link #forEach} accepts non-thread-safe consumers, so it may call the consumer in parallel.
   */
  @NotNull
  @Contract(pure = true)
  default Query<Result> allowParallelProcessing() {
    return this;
  }
}
