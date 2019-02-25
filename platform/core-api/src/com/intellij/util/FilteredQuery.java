// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class FilteredQuery<T> extends AbstractQuery<T> {
  private final Query<T> myOriginal;
  private final Condition<? super T> myFilter;

  public FilteredQuery(@NotNull Query<T> original, @NotNull Condition<? super T> filter) {
    myOriginal = original;
    myFilter = filter;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super T> consumer) {
    return delegateProcessResults(myOriginal, new MyProcessor(consumer));
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(consumer));
  }

  private class MyProcessor implements Processor<T> {
    private final Processor<? super T> myConsumer;

    MyProcessor(@NotNull Processor<? super T> consumer) {
      myConsumer = consumer;
    }

    @Override
    public boolean process(final T t) {
      return !myFilter.value(t) || myConsumer.process(t);
    }
  }
}
