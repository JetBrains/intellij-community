// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps {@linkplain Processor}, check cancellation {@linkplain ProgressManager#checkCanceled()}
 * Cancellation check is throttled: it is done only each Nth {@linkplain #process(Object)} call (default N=16)
 */
@ApiStatus.Internal
public class ProcessorWithThrottledCancellationCheck<V> implements Processor<V> {
  private static final int DEFAULT_CHECK_CANCELLED_EACH = 16;

  private final Processor<? super V> wrapped;
  private final int checkCancelledEach;
  private int iterationNo;

  public ProcessorWithThrottledCancellationCheck(@NotNull Processor<? super V> wrapped) {
    this(wrapped, DEFAULT_CHECK_CANCELLED_EACH);
  }

  public ProcessorWithThrottledCancellationCheck(@NotNull Processor<? super V> wrapped,
                                                 int checkCancelledEach) {
    if (checkCancelledEach <= 0) {
      throw new IllegalArgumentException("checkCancelledEach(=" + checkCancelledEach + ") must be positive");
    }
    this.wrapped = wrapped;
    this.checkCancelledEach = checkCancelledEach;
  }

  @Override
  public boolean process(V v) {
    //don't check cancellation on each iteration, since it may affect performance too much -- check each Nth iteration
    iterationNo++;
    if (iterationNo >= checkCancelledEach) {
      iterationNo = 0;
      ProgressManager.checkCanceled();
    }

    return wrapped.process(v);
  }
}
