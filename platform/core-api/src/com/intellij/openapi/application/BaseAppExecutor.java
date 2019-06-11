// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Callable;

/**
 * DO NOT USE DIRECTLY
 * @see AppExecutor
 * @see AppUIExecutor
 */
@ApiStatus.Internal
public interface BaseAppExecutor <E extends BaseAppExecutor<E>> {
   /**
   * @return an executor that invokes runnables only when indices have been built and are available to use. Automatically expires when the project is disposed.
   * @see com.intellij.openapi.project.DumbService#isDumb(Project)
   */
  @NotNull
  @Contract(pure=true)
  E inSmartMode(@NotNull Project project);

  /**
   * @return an executor that invokes runnables only when read actions are allowed.
   * @see Application#runReadAction(com.intellij.openapi.util.Computable)
   */
  @NotNull
  @Contract(pure=true)
  E inReadAction();

  /**
   * @return an executor that no longer invokes the given runnable after the supplied Disposable is disposed
   */
  @NotNull
  @Contract(pure=true)
  E expireWith(@NotNull Disposable parentDisposable);

  /**
   * Schedule execution of the given task.
   */
  void execute(@NotNull Runnable command);

  /**
   * Schedule the given task's execution and return a Promise that allows to get the result when the task is complete,
   * or cancel the task if it's no longer needed.
   */
  <T> CancellablePromise<T> submit(@NotNull Callable<T> task);

  /**
   * Schedule the given task's execution and return a Promise that allows to check if the task is complete,
   * or cancel the task if it's no longer needed.
   */
  CancellablePromise<?> submit(@NotNull Runnable task);
}
