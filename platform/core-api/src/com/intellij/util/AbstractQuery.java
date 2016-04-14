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
    List<Result> result = new ArrayList<Result>();
    Processor<Result> processor = Processors.cancelableCollectProcessor(result);
    forEach(processor);
    return result;
  }

  @Override
  public Iterator<Result> iterator() {
    assertNotProcessing();
    return new UnmodifiableIterator<Result>(findAll().iterator());
  }

  @Override
  @Nullable
  public Result findFirst() {
    assertNotProcessing();
    final CommonProcessors.FindFirstProcessor<Result> processor = new CommonProcessors.FindFirstProcessor<Result>();
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
  public boolean forEach(@NotNull Processor<Result> consumer) {
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
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<Result> consumer) {
    return AsyncUtil.wrapBoolean(forEach(consumer));
  }

  protected abstract boolean processResults(@NotNull Processor<Result> consumer);

  @NotNull
  protected AsyncFuture<Boolean> processResultsAsync(@NotNull Processor<Result> consumer) {
    return AsyncUtil.wrapBoolean(processResults(consumer));
  }

  @NotNull
  public static <T> Query<T> wrapInReadAction(@NotNull final Query<T> query) {
    return new AbstractQuery<T>() {
      @Override
      protected boolean processResults(@NotNull Processor<T> consumer) {
        return query.forEach(ReadActionProcessor.wrapInReadAction(consumer));
      }
    };
  }
}
