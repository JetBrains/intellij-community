// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public interface BlockingProgressIndicator extends ProgressIndicator {
  /**
   * @param isSynchronousHeadlessExecution indicates that this progress is starts for a backgroundable task in synchronous mode on EDT.
   *                                       These conditions should not lead to an honest initialization of modal progresses.
   *
   * @deprecated Do not use, it's too low level and dangerous. Instead, consider using run* methods in {@link com.intellij.openapi.progress.ProgressManager} or {@link ProgressRunner}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default void startBlocking(@NotNull Runnable init, boolean isSynchronousHeadlessExecution, @NotNull CompletableFuture<?> stopCondition) {
    startBlocking(init, stopCondition);
  }

  /**
   * @deprecated Do not use, it's too low level and dangerous. Instead, consider using run* methods in {@link com.intellij.openapi.progress.ProgressManager} or {@link ProgressRunner}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default void startBlocking(@NotNull Runnable init, @NotNull CompletableFuture<?> stopCondition) {
    throw new UnsupportedOperationException();
  }
}