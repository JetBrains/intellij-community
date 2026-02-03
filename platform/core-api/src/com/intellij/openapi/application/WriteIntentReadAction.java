// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * See <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">Threading Model</a>
 *
 * @see ReadAction
 * @see WriteAction
 */
@ApiStatus.Experimental
public abstract class WriteIntentReadAction {
  /**
   * @see Application#runWriteIntentReadAction(ThrowableComputable)
   */
  @ApiStatus.Experimental
  public static void run(@NotNull Runnable action) {
    computeThrowable(() -> {
      action.run();
      return null;
    });
  }

  /**
   * @see Application#runWriteIntentReadAction(ThrowableComputable)
   */
  @ApiStatus.Experimental
  public static <E extends Throwable> void runThrowable(@NotNull ThrowableRunnable<E> action) throws E {
    computeThrowable((ThrowableComputable<Object, E>)() -> {
      action.run();
      return null;
    });
  }

  /**
   * @see Application#runWriteIntentReadAction(ThrowableComputable)
   */
  @ApiStatus.Experimental
  public static <T> T compute(@NotNull Computable<T> action) {
    return ApplicationManager.getApplication().runWriteIntentReadAction(() -> {
      return action.compute();
    });
  }

  /**
   * @see Application#runWriteIntentReadAction(ThrowableComputable)
   */
  @ApiStatus.Experimental
  public static <T, E extends Throwable> T computeThrowable(@NotNull ThrowableComputable<T, E> action) throws E {
    return ApplicationManager.getApplication().runWriteIntentReadAction(action);
  }
}
