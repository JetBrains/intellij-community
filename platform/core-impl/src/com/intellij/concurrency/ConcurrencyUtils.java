// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.CoroutinesKt;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.ProgressIndicatorUtilsCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

@ApiStatus.Internal
public final class ConcurrencyUtils {
  private ConcurrencyUtils() { }

  /// Executes [action] and ensures it's under [ProgressIndicator] (i.e. [ProgressIndicatorProvider#getGlobalProgressIndicator] does return a real non-null indicator there).
  /// This indicator will be either:
  /// - the current [ProgressIndicatorProvider#getGlobalProgressIndicator] if it's already installed, or
  /// - an indicator corresponding to the context [Cancellation#currentJob()] if there's one, or
  /// - an [EmptyProgressIndicator] otherwise.
  public static <T> T runWithIndicatorOrContextCancellation(@NotNull Function<? super @NotNull ProgressIndicator, ? extends T> action) {
    ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator == null && Cancellation.currentJob() != null) {
      return CoroutinesKt.blockingContextToIndicator(() -> action.apply(ProgressIndicatorProvider.getGlobalProgressIndicator()));
    }
    return ProgressIndicatorUtilsCore.runUnderEmptyProgressIfNone(()-> action.apply(ProgressIndicatorProvider.getGlobalProgressIndicator()));
  }
}
