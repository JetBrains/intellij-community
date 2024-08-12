// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.progress.ProgressManager;

import java.util.Objects;

public abstract class CompletionThreadingBase implements CompletionThreading {
  protected static final ThreadLocal<Boolean> isInBatchUpdate = ThreadLocal.withInitial(() -> Boolean.FALSE);

  public static void withBatchUpdate(Runnable runnable, CompletionProcess process) {
    if (isInBatchUpdate.get().booleanValue() || !(process instanceof CompletionProgressIndicator)) {
      runnable.run();
      return;
    }

    try {
      isInBatchUpdate.set(Boolean.TRUE);
      runnable.run();
      ProgressManager.checkCanceled();
      CompletionProgressIndicator currentIndicator = (CompletionProgressIndicator)process;
      CompletionThreadingBase threading = Objects.requireNonNull(currentIndicator.getCompletionThreading());
      threading.flushBatchResult(currentIndicator);
    } finally {
      isInBatchUpdate.set(Boolean.FALSE);
    }
  }

  protected abstract void flushBatchResult(CompletionProgressIndicator indicator);
}
