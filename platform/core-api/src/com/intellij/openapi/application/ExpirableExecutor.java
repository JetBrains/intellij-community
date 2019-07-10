// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * An executor that allows cancelling given runnables on disposal of [Disposable]s associated using [expireWith]. This also ensures that
 * if the task is a coroutine suspended at some execution point, it's resumed with a [CancellationException] giving the coroutine a chance
 * to clean up any resources it might have acquired before suspending. The executor is created by calling {@link #on}, the expirations are
 * specified by chained calls. For example, to invoke some action that cancels when project is disposed, one can use
 * {@code ExpirableExecutor.on(AppExecutorUtil.getAppExecutorService()).expireWith(project).
 */

@ApiStatus.Experimental
public interface ExpirableExecutor extends BaseExpirableExecutor<ExpirableExecutor> {

  /**
   * Creates constrained executor from provided executor
   */
  @NotNull
  static ExpirableExecutor on(@NotNull Executor executor) {
    return AsyncExecutionService.getService().createExecutor(executor);
  }
}
