// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import org.jetbrains.annotations.NotNull;

public abstract class PostProcessingQuery<B, R> extends AbstractQuery<R> {

  private final Query<? extends B> myBaseQuery;

  public PostProcessingQuery(@NotNull Query<? extends B> query) {
    myBaseQuery = query;
  }

  @Override
  protected final boolean processResults(@NotNull Processor<? super R> consumer) {
    return delegateProcessResults(myBaseQuery, adapt(consumer));
  }

  @NotNull
  @Override
  public final AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super R> consumer) {
    return myBaseQuery.forEachAsync(adapt(consumer));
  }

  private Processor<? super B> adapt(Processor<? super R> r) {
    return b -> process(b, r);
  }

  protected abstract boolean process(B b, @NotNull Processor<? super R> resultProcessor);
}
