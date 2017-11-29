/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    this(original, ContainerUtil.canonicalStrategy(), (Function<T, M>)FunctionUtil.<M>id());
  }

  public UniqueResultsQuery(@NotNull Query<T> original, @NotNull TObjectHashingStrategy<M> hashingStrategy) {
    this(original, hashingStrategy, (Function<T, M>)FunctionUtil.<M>id());
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
  public boolean forEach(@NotNull final Processor<T> consumer) {
    return process(Collections.synchronizedSet(new THashSet<>(myHashingStrategy)), consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<T> consumer) {
    return processAsync(Collections.synchronizedSet(new THashSet<>(myHashingStrategy)), consumer);
  }

  private boolean process(@NotNull Set<M> processedElements, @NotNull Processor<T> consumer) {
    return myOriginal.forEach(new MyProcessor(processedElements, consumer));
  }

  @NotNull
  private AsyncFuture<Boolean> processAsync(@NotNull Set<M> processedElements, @NotNull Processor<T> consumer) {
    return myOriginal.forEachAsync(new MyProcessor(processedElements, consumer));
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    List<T> result = Collections.synchronizedList(new ArrayList<T>());
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
    private final Processor<T> myConsumer;

    public MyProcessor(@NotNull Set<M> processedElements, @NotNull Processor<T> consumer) {
      myProcessedElements = processedElements;
      myConsumer = consumer;
    }

    @Override
    public boolean process(final T t) {
      ProgressManager.checkCanceled();
      return !myProcessedElements.add(myMapper.fun(t)) || myConsumer.process(t);
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String toString() {
    return "UniqueQuery: "+myOriginal;
  }
}
