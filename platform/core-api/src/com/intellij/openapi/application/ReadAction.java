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
package com.intellij.openapi.application;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

public abstract class ReadAction<T> extends BaseActionRunnable<T> {
  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  @NotNull
  @Override
  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<>(this);
    return compute(() -> result.run());
  }

  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  public static AccessToken start() {
    return ApplicationManager.getApplication().acquireReadActionLock();
  }

  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  @Override
  protected abstract void run(@NotNull Result<T> result) throws Throwable;

  public static <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E {
    compute(() -> {action.run(); return null; });
  }

  public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> action) throws E {
    return ApplicationManager.getApplication().runReadAction(action);
  }

  /**
   * Create an {@link NonBlockingReadAction} builder to run the given Runnable in non-blocking read action on a background thread.
   */
  @NotNull
  @Contract(pure=true)
  public static NonBlockingReadAction<Void> nonBlocking(@NotNull Runnable task) {
    return nonBlocking(() -> {
      task.run();
      return null;
    });
  }

  /**
   * Create an {@link NonBlockingReadAction} builder to run the given Callable in a non-blocking read action on a background thread.
   */
  @NotNull
  @Contract(pure=true)
  public static <T> NonBlockingReadAction<T> nonBlocking(@NotNull Callable<T> task) {
    return AsyncExecutionService.getService().buildNonBlockingReadAction(task);
  }

}