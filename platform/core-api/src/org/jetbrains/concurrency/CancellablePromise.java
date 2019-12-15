// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.function.Consumer;

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