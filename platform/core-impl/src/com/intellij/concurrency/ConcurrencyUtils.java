// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.CoroutinesKt;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ConcurrencyUtils {
  private ConcurrencyUtils() { }

  public static <T> T runWithIndicatorOrContextCancellation(@NotNull Function<? super @NotNull ProgressIndicator, ? extends T> action) {
    return runWithIndicatorOrContextCancellation(EmptyProgressIndicator::new, action);
  }

  /**
   * Executes {@code action} under {@link ProgressIndicator} that corresponds to the context {@link Cancellation#currentJob()}.
   * Otherwise, runs {@code action} under the installed {@link ProgressIndicatorProvider#getGlobalProgressIndicator()}.
   */
  public static <T> T runWithIndicatorOrContextCancellation(
    @NotNull Supplier<? extends @NotNull ProgressIndicator> defaultIndicatorSupplier,
    @NotNull Function<? super @NotNull ProgressIndicator, ? extends T> action
  ) {
    ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    //noinspection TestOnlyProblems
    if (progressIndicator == null && Cancellation.currentJob() != null) {
      return CoroutinesKt.blockingContextToIndicator(() -> action.apply(ProgressIndicatorProvider.getGlobalProgressIndicator()));
    }
    else {
      ProgressIndicator actualIndicator = progressIndicator == null ? defaultIndicatorSupplier.get() : progressIndicator;
      return action.apply(actualIndicator);
    }
  }
}
