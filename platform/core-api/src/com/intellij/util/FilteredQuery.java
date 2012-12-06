/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author max
 */
public class FilteredQuery<T> implements Query<T> {
  private final Query<T> myOriginal;
  private final Condition<T> myFilter;

  public FilteredQuery(final Query<T> original, Condition<T> filter) {
    myOriginal = original;
    myFilter = filter;
  }

  @Override
  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<T>();
    forEach(processor);
    return processor.getFoundValue();
  }

  @Override
  public boolean forEach(@NotNull final Processor<T> consumer) {
    myOriginal.forEach(new MyProcessor(consumer));
    return true;
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(consumer));
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    CommonProcessors.CollectProcessor<T> processor = new CommonProcessors.CollectProcessor<T>();
    forEach(processor);
    return processor.getResults();
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    return findAll().toArray(a);
  }

  @Override
  public Iterator<T> iterator() {
    return findAll().iterator();
  }

  private class MyProcessor implements Processor<T> {
    private final Processor<T> myConsumer;

    public MyProcessor(Processor<T> consumer) {
      myConsumer = consumer;
    }

    @Override
    public boolean process(final T t) {
      if (!myFilter.value(t)) return true;
      if (!myConsumer.process(t)) return false;

      return true;
    }
  }
}
