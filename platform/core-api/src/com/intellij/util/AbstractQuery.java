// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.openapi.application.ReadActionProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
public abstract class AbstractQuery<Result> implements Query<Result> {
  private boolean myIsProcessing;

  @Override
  @NotNull
  public Collection<Result> findAll() {
    assertNotProcessing();
    List<Result> result = new ArrayList<>();
    Processor<Result> processor = Processors.cancelableCollectProcessor(result);
    forEach(processor);
    return result;
  }

  @NotNull
  @Override
  public Iterator<Result> iterator() {
    assertNotProcessing();
    return new UnmodifiableIterator<>(findAll().iterator());
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
    assert !myIsProcessing : "Operation is not allowed while query is being processed";
  }

  @NotNull
  @Override
  public Result[] toArray(@NotNull Result[] a) {
    assertNotProcessing();

    final Collection<Result> all = findAll();
    return all.toArray(a);
  }

  @Override
  public boolean forEach(@NotNull Processor<? super Result> consumer) {
    assertNotProcessing();

    myIsProcessing = true;
    try {
      return processResults(consumer);
    }
    finally {
      myIsProcessing = false;
    }
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super Result> consumer) {
    return AsyncUtil.wrapBoolean(forEach(consumer));
  }

  protected abstract boolean processResults(@NotNull Processor<? super Result> consumer);

  @NotNull
  protected AsyncFuture<Boolean> processResultsAsync(@NotNull Processor<? super Result> consumer) {
    return AsyncUtil.wrapBoolean(processResults(consumer));
  }

  @NotNull
  public static <T> Query<T> wrapInReadAction(@NotNull final Query<T> query) {
    return new AbstractQuery<T>() {
      @Override
      protected boolean processResults(@NotNull Processor<? super T> consumer) {
        return query.forEach(ReadActionProcessor.wrapInReadAction(consumer));
      }
    };
  }
}
