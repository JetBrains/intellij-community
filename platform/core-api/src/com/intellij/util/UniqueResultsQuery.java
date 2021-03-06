// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.progress.ProgressManager;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class UniqueResultsQuery<T, M> extends AbstractQuery<T> {
  @NotNull private final Query<? extends T> myOriginal;
  @Nullable private final Hash.Strategy<? super M> myHashingStrategy;
  @NotNull private final Function<? super T, ? extends M> myMapper;

  public UniqueResultsQuery(@NotNull Query<? extends T> original) {
    this(original, Functions.identity());
  }

  public UniqueResultsQuery(@NotNull Query<? extends T> original, @NotNull Hash.Strategy<? super M> hashingStrategy) {
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
    return ObjectSets.synchronize(new ObjectOpenCustomHashSet<>(myHashingStrategy));
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
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
