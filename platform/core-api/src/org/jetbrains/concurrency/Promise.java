// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency;

import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * Please use <a href="https://plugins.jetbrains.com/docs/intellij/kotlin-coroutines.html">Kotlin coroutines</a> instead
 * </p>
 * <hr>
 *
 * The Promise represents the eventual completion (or failure) of an asynchronous operation, and its resulting value.
 * <p>
 * A Promise is a proxy for a value not necessarily known when the promise is created.
 * It allows you to associate handlers with an asynchronous action's eventual success value or failure reason.
 * This lets asynchronous methods return values like synchronous methods: instead of immediately returning the final value,
 * the asynchronous method returns a promise to supply the value at some point in the future.
 * <p>
 * A Promise is in one of these states:
 *
 * <ul>
 *   <li>pending: initial state, neither fulfilled nor rejected.</li>
 *   <li>succeeded: meaning that the operation completed successfully.</li>
 *   <li>rejected: meaning that the operation failed.</li>
 * </ul>
 */
@ApiStatus.Obsolete
public interface Promise<T> {
  enum State {
    PENDING, SUCCEEDED, REJECTED
  }

  /**
   * Execute passed handler on promise resolve and return a promise with a transformed result value.
   *
   * <pre>
   * {@code
   *
   * somePromise
   *  .then(it -> transformOrProcessValue(it))
   * }
   * </pre>
   */
  @NotNull
  <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done);

  /**
   * The same as {@link #then(Function)}, but the handler can be asynchronous.
   *
   * <pre>
   * {@code
   *
   * somePromise
   *  .then(it -> transformOrProcessValue(it))
   *  .thenAsync(it -> processValueAsync(it))
   * }
   * </pre>
   */
  @NotNull
  <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, ? extends Promise<SUB_RESULT>> done);

  /**
   * Execute passed handler on promise resolve.
   */
  @NotNull
  Promise<T> onSuccess(@NotNull Consumer<? super T> handler);

  /**
   * Execute passed handler on promise reject.
   */
  @NotNull
  Promise<T> onError(@NotNull Consumer<? super Throwable> rejected);

  /**
   * Resolve or reject passed promise as soon as this promise is resolved or rejected.
   */
  @NotNull
  Promise<T> processed(@NotNull Promise<? super T> child);

  /**
   * Execute passed handler on promise resolve (result value will be passed),
   * or on promise reject (null as result value will be passed).
   */
  @NotNull
  Promise<T> onProcessed(@NotNull Consumer<? super @Nullable T> processed);

  /**
   * Get promise state.
   */
  @NotNull
  State getState();

  @Nullable
  T blockingGet(int timeout, @NotNull TimeUnit timeUnit) throws TimeoutException, ExecutionException;

  default @Nullable T blockingGet(int timeout) throws TimeoutException, ExecutionException {
    return blockingGet(timeout, TimeUnit.MILLISECONDS);
  }

  default boolean isSucceeded() {
    return getState() == State.SUCCEEDED;
  }
}