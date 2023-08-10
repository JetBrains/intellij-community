// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;


/**
 * A lazy value that is guaranteed to be computed only on a pooled thread without a RA lock.
 * May be safely called from any thread.
 */
final class SafeNullableLazyValue<T> {

  private final Supplier<? extends T> myComputable;
  private final AtomicReference<@Nullable Boolean> myIsComputedRef = new AtomicReference<>(FALSE); // null == in progress
  private volatile @Nullable T myValue = null;

  private SafeNullableLazyValue(final @NotNull Supplier<? extends T> computable) {
    myComputable = computable;
  }

  public boolean isComputed() {
    return TRUE.equals(myIsComputedRef.get());
  }

  private boolean isNotComputedNorInProgress() {
    return FALSE.equals(myIsComputedRef.get());
  }

  /**
   * Identical to {@code this.getValueOrElse(null)}.
   *
   * @see #getValueOrElse(Object)
   */
  public @Nullable T getValue() {
    return getValueOrElse(null);
  }

  /**
   * If possible, tries to compute this lazy value synchronously.
   * Otherwise, schedules an asynchronous computation if necessary and returns {@code notYet}.
   *
   * @return a computed nullable value, or {@code notYet}.
   * @implNote this method blocks on synchronous computation.
   * @see #getValue()
   */
  public @Nullable T getValueOrElse(final @Nullable T notYet) {
    if (isComputed()) {
      return myValue;
    }

    final var app = ApplicationManager.getApplication();
    if (app.isDispatchThread() || app.isReadAccessAllowed()) {
      if (isNotComputedNorInProgress()) {
        CompletableFuture.runAsync(this::compute, AppExecutorUtil.getAppExecutorService());
      }
      return notYet;
    }

    return compute();
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  private @Nullable T compute() {
    if (isComputed()) {
      return myValue;
    }
    synchronized (this) {
      if (isComputed()) {
        return myValue;
      }
      myIsComputedRef.set(null);
      try {
        final var stamp = RecursionManager.markStack();
        final var value = myComputable.get();
        if (stamp.mayCacheNow()) {
          myValue = value;
          myIsComputedRef.set(TRUE);
        }
        return value;
      }
      finally {
        myIsComputedRef.compareAndSet(null, FALSE);
      }
    }
  }

  static <T> @NotNull SafeNullableLazyValue<@Nullable T> create(final @NotNull Supplier<? extends T> computable) {
    return new SafeNullableLazyValue<>(computable);
  }
}
