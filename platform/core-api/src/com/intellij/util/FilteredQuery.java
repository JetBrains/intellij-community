// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class FilteredQuery<T> implements Query<T> {
  private final Query<T> myOriginal;
  private final Condition<T> myFilter;

  public FilteredQuery(@NotNull Query<T> original, @NotNull Condition<T> filter) {
    myOriginal = original;
    myFilter = filter;
  }

  @Override
  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<>();
    forEach(processor);
    return processor.getFoundValue();
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    myOriginal.forEach(new MyProcessor(consumer));
    return true;
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(consumer));
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    List<T> result = new ArrayList<>();
    Processor<T> processor = Processors.cancelableCollectProcessor(result);
    forEach(processor);
    return result;
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    return findAll().toArray(a);
  }

  @Override
  @NotNull
  public Iterator<T> iterator() {
    return findAll().iterator();
  }

  private class MyProcessor implements Processor<T> {
    private final Processor<? super T> myConsumer;

    public MyProcessor(@NotNull Processor<? super T> consumer) {
      myConsumer = consumer;
    }

    @Override
    public boolean process(final T t) {
      return !myFilter.value(t) || myConsumer.process(t);
    }
  }
}
