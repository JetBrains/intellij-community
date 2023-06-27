// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.util.ThrowableComputable;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.CancellationException;

import static kotlinx.coroutines.JobKt.ensureActive;

@Internal
public final class Cancellation {

  private Cancellation() { }

  @VisibleForTesting
  public static @Nullable Job currentJob() {
    return ThreadContext.currentThreadContext().get(Job.Key);
  }

  public static @Nullable CompletableJob getJob(@NotNull CoroutineContext context) {
    Job job = context.get(Job.Key);
    return job instanceof CompletableJob ? (CompletableJob)job : null;
  }

  public static void checkCancelled() {
    Job currentJob = currentJob();
    if (currentJob != null) {
      try {
        ensureActive(currentJob);
      }
      catch (CancellationException e) {
        throw new JobCanceledException(e);
      }
    }
  }

  /**
   * {@code true} if running in non-cancelable section started with {@link #computeInNonCancelableSection)} in this thread,
   * otherwise {@code false}
   */
  // do not supply initial value to conserve memory
  private static final ThreadLocal<Boolean> isInNonCancelableSection = new ThreadLocal<>();

  public static boolean isInNonCancelableSection() {
    return isInNonCancelableSection.get() != null;
  }

  public static <T, E extends Exception> T computeInNonCancelableSection(@NotNull ThrowableComputable<T, E> computable) throws E {
    try {
      if (isInNonCancelableSection()) {
        return computable.compute();
      }
      try {
        isInNonCancelableSection.set(Boolean.TRUE);
        return computable.compute();
      }
      finally {
        isInNonCancelableSection.remove();
      }
    }
    catch (ProcessCanceledException e) {
      throw new RuntimeException("PCE is not expected in non-cancellable section execution", e);
    }
  }

  public static @NotNull AccessToken withNonCancelableSection() {
    if (isInNonCancelableSection()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    isInNonCancelableSection.set(Boolean.TRUE);
    return new AccessToken() {
      @Override
      public void finish() {
        isInNonCancelableSection.remove();
      }
    };
  }
}
