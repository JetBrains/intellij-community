// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import org.jetbrains.annotations.NotNull;

/**
 * @param <S> source type
 * @param <T> target type
 */
public class InstanceofQuery<S, T> extends AbstractQuery<T> {
  private final Class<? extends T>[] myClasses;
  private final Query<S> myDelegate;

  public InstanceofQuery(Query<S> delegate, Class<? extends T>... aClasses) {
    myClasses = aClasses;
    myDelegate = delegate;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super T> consumer) {
    return delegateProcessResults(myDelegate, new MyProcessor(consumer));
  }

  @Override
  public @NotNull AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return myDelegate.forEachAsync(new MyProcessor(consumer));
  }

  private class MyProcessor implements Processor<S> {
    private final Processor<? super T> myConsumer;

    MyProcessor(Processor<? super T> consumer) {
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
