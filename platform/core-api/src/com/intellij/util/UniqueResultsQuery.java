// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class UniqueResultsQuery<T, M> extends AbstractQuery<T> {
  @NotNull private final Query<? extends T> myOriginal;
  @NotNull private final TObjectHashingStrategy<? super M> myHashingStrategy;
  @NotNull private final Function<? super T, ? extends M> myMapper;

  public UniqueResultsQuery(@NotNull Query<? extends T> original) {
    this(original, ContainerUtil.canonicalStrategy(), Functions.identity());
  }

  public UniqueResultsQuery(@NotNull Query<? extends T> original, @NotNull TObjectHashingStrategy<? super M> hashingStrategy) {
    this(original, hashingStrategy, Functions.identity());
  }

  public UniqueResultsQuery(@NotNull Query<? extends T> original, @NotNull TObjectHashingStrategy<? super M> hashingStrategy, @NotNull Function<? super T, ? extends M> mapper) {
    myOriginal = original;
    myHashingStrategy = hashingStrategy;
    myMapper = mapper;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super T> consumer) {
    return delegateProcessResults(myOriginal, new MyProcessor(Collections.synchronizedSet(new THashSet<>(myHashingStrategy)), consumer));
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(Collections.synchronizedSet(new THashSet<>(myHashingStrategy)), consumer));
  }

  private class MyProcessor implements Processor<T> {
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
