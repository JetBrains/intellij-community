// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class UniqueResultsQuery<T, M> extends AbstractQuery<T> {
  private final @NotNull Query<? extends T> myOriginal;
  private final HashingStrategy<? super M> myHashingStrategy;
  private final @NotNull Function<? super T, ? extends M> myMapper;

  public UniqueResultsQuery(@NotNull Query<? extends T> original) {
    this(original, Functions.identity());
  }

  public UniqueResultsQuery(@NotNull Query<? extends T> original, @NotNull HashingStrategy<? super M> hashingStrategy) {
    myOriginal = original;
    myHashingStrategy = hashingStrategy;
    myMapper = Functions.identity();
  }

  public UniqueResultsQuery(@NotNull Query<? extends T> original, @NotNull Function<? super T, ? extends M> mapper) {
    myOriginal = original;
    myHashingStrategy = null;
    myMapper = mapper;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super T> consumer) {
    return delegateProcessResults(myOriginal, new MyProcessor(createSet(), consumer));
  }

  private @NotNull Set<M> createSet() {
    if (myHashingStrategy == null) {
      return Collections.synchronizedSet(new HashSet<>());
    }
    return Collections.synchronizedSet(CollectionFactory.createCustomHashingStrategySet(myHashingStrategy));
  }

  @Override
  public @NotNull AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(createSet(), consumer));
  }

  private final class MyProcessor implements Processor<T> {
    private final Set<? super M> myProcessedElements;
    private final Processor<? super T> myConsumer;

    MyProcessor(@NotNull Set<? super M> processedElements, @NotNull Processor<? super T> consumer) {
      myProcessedElements = processedElements;
      myConsumer = consumer;
    }

    @Override
    public boolean process(final T t) {
      ProgressManager.checkCanceled();
      // in case of exception do not mark the element as processed, we couldn't recover otherwise
      M m = myMapper.fun(t);
      if (myProcessedElements.contains(m)) return true;
      boolean result = myConsumer.process(t);
      myProcessedElements.add(m);
      return result;
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String toString() {
    return "UniqueQuery: "+myOriginal;
  }
}
