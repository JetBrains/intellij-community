// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncFutureFactory;
import com.intellij.concurrency.AsyncFutureResult;
import com.intellij.concurrency.DefaultResultConsumer;
import com.intellij.util.concurrency.SameThreadExecutor;
import org.jetbrains.annotations.NotNull;

public class MergeQuery<T> extends AbstractQuery<T>{
  private final Query<? extends T> myQuery1;
  private final Query<? extends T> myQuery2;

  public MergeQuery(@NotNull Query<? extends T> query1, @NotNull Query<? extends T> query2) {
    myQuery1 = query1;
    myQuery2 = query2;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super T> consumer) {
    return delegateProcessResults(myQuery1, consumer) && delegateProcessResults(myQuery2, consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull final Processor<? super T> consumer) {
    final AsyncFutureResult<Boolean> result = AsyncFutureFactory.getInstance().createAsyncFutureResult();

    AsyncFuture<Boolean> fq = myQuery1.forEachAsync(consumer);

    fq.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(result) {
      @Override
      public void onSuccess(Boolean value) {
        if (value.booleanValue()) {
          AsyncFuture<Boolean> fq2 = myQuery2.forEachAsync(consumer);
          fq2.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<>(result));
        }
        else {
          result.set(false);
        }
      }
    });
    return result;
  }

}
