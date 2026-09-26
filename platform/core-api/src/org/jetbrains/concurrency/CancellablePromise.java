// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * Please use <a href="https://plugins.jetbrains.com/docs/intellij/kotlin-coroutines.html">Kotlin coroutines</a> instead
 * </p>
 */
@ApiStatus.Obsolete
public interface CancellablePromise<T> extends Promise<T>, Future<T> {
  @NotNull
  @Override
  CancellablePromise<T> onSuccess(@NotNull Consumer<? super T> handler);

  @NotNull
  @Override
  CancellablePromise<T> onError(@NotNull Consumer<? super Throwable> rejected);

  @NotNull
  @Override
  CancellablePromise<T> onProcessed(@NotNull Consumer<? super T> processed);

  void cancel();
}