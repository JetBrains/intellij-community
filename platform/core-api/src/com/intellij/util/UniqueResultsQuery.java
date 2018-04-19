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
public class UniqueResultsQuery<T, M> implements Query<T> {
  @NotNull private final Query<T> myOriginal;
  @NotNull private final TObjectHashingStrategy<M> myHashingStrategy;
  @NotNull private final Function<T, M> myMapper;

  public UniqueResultsQuery(@NotNull Query<T> original) {
    //noinspection unchecked
    this(original, ContainerUtil.canonicalStrategy(), Function.ID);
  }

  public UniqueResultsQuery(@NotNull Query<T> original, @NotNull TObjectHashingStrategy<M> hashingStrategy) {
    //noinspection unchecked
    this(original, hashingStrategy, Function.ID);
  }

  public UniqueResultsQuery(@NotNull Query<T> original, @NotNull TObjectHashingStrategy<M> hashingStrategy, @NotNull Function<T, M> mapper) {
    myOriginal = original;
    myHashingStrategy = hashingStrategy;
    myMapper = mapper;
  }

  @Override
  public T findFirst() {
    return myOriginal.findFirst();
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    return process(Collections.synchronizedSet(new THashSet<>(myHashingStrategy)), consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return processAsync(Collections.synchronizedSet(new THashSet<>(myHashingStrategy)), consumer);
  }

  private boolean process(@NotNull Set<M> processedElements, @NotNull Processor<? super T> consumer) {
    return myOriginal.forEach(new MyProcessor(processedElements, consumer));
  }

  @NotNull
  private AsyncFuture<Boolean> processAsync(@NotNull Set<M> processedElements, @NotNull Processor<? super T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(processedElements, consumer));
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    List<T> result = Collections.synchronizedList(new ArrayList<>());
    Processor<T> processor = Processors.cancelableCollectProcessor(result);
    forEach(processor);
    return result;
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    return findAll().toArray(a);
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return findAll().iterator();
  }

  private class MyProcessor implements Processor<T> {
    private final Set<M> myProcessedElements;
    private final Processor<? super T> myConsumer;

    public MyProcessor(@NotNull Set<M> processedElements, @NotNull Processor<? super T> consumer) {
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
