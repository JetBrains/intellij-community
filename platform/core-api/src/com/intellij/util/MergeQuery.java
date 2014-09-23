/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    List<T> results = new ArrayList<T>();
    forEach(new CommonProcessors.CollectProcessor<T>(results));
    return results;
  }

  @Override
  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<T>();
    forEach(processor);
    return processor.getFoundValue();
  }

  @Override
  public boolean forEach(@NotNull final Processor<T> consumer) {
    return processSubQuery(consumer, myQuery1) && processSubQuery(consumer, myQuery2);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull final Processor<T> consumer) {
    final AsyncFutureResult<Boolean> result = AsyncFutureFactory.getInstance().createAsyncFutureResult();

    final AsyncFuture<Boolean> fq = processSubQueryAsync(consumer, myQuery1);

    fq.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(result) {
      @Override
      public void onSuccess(Boolean value) {
        if (value.booleanValue()) {
          final AsyncFuture<Boolean> fq2 = processSubQueryAsync(consumer, myQuery2);
          fq2.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(result));
        }
        else {
          result.set(false);
        }
      }
    });
    return result;
  }


  private <V extends T> boolean processSubQuery(@NotNull final Processor<T> consumer, @NotNull Query<V> query1) {
    // Query.forEach(Processor<T> consumer) should be actually Query.forEach(Processor<? super T> consumer) but it is too late now
    return query1.forEach((Processor<V>)consumer);
  }

  private <V extends T> AsyncFuture<Boolean> processSubQueryAsync(@NotNull final Processor<T> consumer, @NotNull Query<V> query1) {
    return query1.forEachAsync((Processor<V>)consumer);
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
