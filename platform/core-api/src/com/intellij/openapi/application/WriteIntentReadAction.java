// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 *
 * @see ReadAction, WriteAction
 * @see CoroutinesKt#writeIntentAction
 */
@ApiStatus.Experimental
public abstract class WriteIntentReadAction {
  /**
   * @see Application#runWriteIntentReadAction(ThrowableComputable)
   */
  @ApiStatus.Experimental
  public static void run(@NotNull Runnable action) {
    compute((ThrowableComputable<Object, RuntimeException>)() -> {
      action.run();
      return null;
    });
  }

  /**
   * @see Application#runWriteIntentReadAction(ThrowableComputable)
   */
  @ApiStatus.Experimental
  public static <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E {
    compute((ThrowableComputable<Object, E>)() -> {
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
  public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> action) throws E {
    return ApplicationManager.getApplication().runWriteIntentReadAction(action);
  }
}
