// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractQuery<Result> implements Query<Result> {
  private final ThreadLocal<Boolean> myIsProcessing = new ThreadLocal<>();

  // some clients rely on the (accidental) order of found result
  // to discourage them, randomize the results sometimes to induce errors caused by the order reliance
  private static final boolean RANDOMIZE = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal() ;
  private static final Comparator<Object> CRAZY_ORDER = (o1, o2) -> -Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));

  @Override
  @NotNull
  public Collection<Result> findAll() {
    assertNotProcessing();
    List<Result> result = new ArrayList<>();
    Processor<Result> processor = Processors.cancelableCollectProcessor(result);
    forEach(processor);
    if (RANDOMIZE && result.size() > 1) {
      result.sort(CRAZY_ORDER);
    }
    return result;
  }

  @NotNull
  @Override
  public Iterator<Result> iterator() {
    assertNotProcessing();
    return new UnmodifiableIterator<>(Query.super.iterator());
  }

  @Override
  @Nullable
  public Result findFirst() {
    assertNotProcessing();
    final CommonProcessors.FindFirstProcessor<Result> processor = new CommonProcessors.FindFirstProcessor<>();
    forEach(processor);
    return processor.getFoundValue();
  }

  private void assertNotProcessing() {
    assert myIsProcessing.get() == null : "Operation is not allowed while query is being processed";
  }

  @NotNull
  @Override
  public Query<Result> allowParallelProcessing() {
    return new AbstractQuery<Result>() {
      @Override
      protected boolean processResults(@NotNull Processor<? super Result> consumer) {
        return AbstractQuery.this.doProcessResults(consumer);
      }

      @Override
      public boolean forEach(@NotNull Processor<? super Result> consumer) {
        return processResults(consumer);
      }
    };
  }

  @NotNull
  private Processor<Result> threadSafeProcessor(@NotNull Processor<? super Result> consumer) {
    Object lock = ObjectUtils.sentinel("AbstractQuery lock");
    return e -> {
      synchronized (lock) {
        return consumer.process(e);
      }
    };
  }

  @Override
  public boolean forEach(@NotNull Processor<? super Result> consumer) {
    return doProcessResults(threadSafeProcessor(consumer));
  }

  private boolean doProcessResults(@NotNull Processor<? super Result> consumer) {
    assertNotProcessing();

    myIsProcessing.set(true);
    try {
      return processResults(consumer);
    }
    finally {
      myIsProcessing.remove();
    }
  }

  /**
   * Assumes consumer being capable of processing results in parallel
   */
  protected abstract boolean processResults(@NotNull Processor<? super Result> consumer);

  /**
   * Should be called only from {@link #processResults} implementations to delegate to another query
   */
  protected static <T> boolean delegateProcessResults(@NotNull Query<T> query, @NotNull Processor<? super T> consumer) {
    if (query instanceof AbstractQuery) {
      return ((AbstractQuery<T>)query).doProcessResults(consumer);
    }
    return query.forEach(consumer);
  }

  @NotNull
  public static <T> Query<T> wrapInReadAction(@NotNull final Query<? extends T> query) {
    return new AbstractQuery<T>() {
      @Override
      protected boolean processResults(@NotNull Processor<? super T> consumer) {
        return AbstractQuery.delegateProcessResults(query, ReadActionProcessor.wrapInReadAction(consumer));
      }
    };
  }
}
