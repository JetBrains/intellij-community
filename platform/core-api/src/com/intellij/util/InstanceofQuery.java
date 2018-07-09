// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * @param <S> source type
 * @param <T> target type
 */
public class InstanceofQuery<S, T> implements Query<T> {
  private final Class<? extends T>[] myClasses;
  private final Query<S> myDelegate;

  public InstanceofQuery(Query<S> delegate, Class<? extends T>... aClasses) {
    myClasses = aClasses;
    myDelegate = delegate;
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    ArrayList<T> result = new ArrayList<>();
    forEach((Processor<? super T>)o -> {
      result.add(o);
      return true;
    });
    return result;
  }

  @Override
  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<>();
    forEach(processor);
    return processor.getFoundValue();
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    return myDelegate.forEach(new MyProcessor(consumer));
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return myDelegate.forEachAsync(new MyProcessor(consumer));
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull T[] a) {
    final Collection<T> all = findAll();
    return all.toArray(a);
  }

  @Override
  public Iterator<T> iterator() {
    return new UnmodifiableIterator<>(findAll().iterator());
  }

  private class MyProcessor implements Processor<S> {
    private final Processor<? super T> myConsumer;

    public MyProcessor(Processor<? super T> consumer) {
      myConsumer = consumer;
    }

    @Override
    public boolean process(S o) {
      for (Class<? extends T> aClass : myClasses) {
        if (aClass.isInstance(o)) {
          //noinspection unchecked
          return myConsumer.process((T)o);
        }
      }
      return true;
    }
  }
}
