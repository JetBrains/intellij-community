// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util;

import com.intellij.concurrency.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MergeQuery<T> implements Query<T>{
  private final Query<? extends T> myQuery1;
  private final Query<? extends T> myQuery2;

  public MergeQuery(@NotNull Query<? extends T> query1, @NotNull Query<? extends T> query2) {
    myQuery1 = query1;
    myQuery2 = query2;
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    List<T> results = new ArrayList<>();
    Processor<T> processor = Processors.cancelableCollectProcessor(results);
    forEach(processor);
    return results;
  }

  @Override
  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<>();
    forEach(processor);
    return processor.getFoundValue();
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    return processSubQuery(myQuery1, consumer) && processSubQuery(myQuery2, consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull final Processor<? super T> consumer) {
    final AsyncFutureResult<Boolean> result = AsyncFutureFactory.getInstance().createAsyncFutureResult();

    final AsyncFuture<Boolean> fq = processSubQueryAsync(myQuery1, consumer);

    fq.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(result) {
      @Override
      public void onSuccess(Boolean value) {
        if (value.booleanValue()) {
          final AsyncFuture<Boolean> fq2 = processSubQueryAsync(myQuery2, consumer);
          fq2.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<>(result));
        }
        else {
          result.set(false);
        }
      }
    });
    return result;
  }


  private <V extends T> boolean processSubQuery(@NotNull Query<V> subQuery, @NotNull final Processor<? super T> consumer) {
    return subQuery.forEach(consumer);
  }

  private <V extends T> AsyncFuture<Boolean> processSubQueryAsync(@NotNull Query<V> query1, @NotNull final Processor<? super T> consumer) {
    return query1.forEachAsync(consumer);
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    final Collection<T> results = findAll();
    return results.toArray(a);
  }

  @Override
  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}
