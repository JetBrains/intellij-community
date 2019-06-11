// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * An executor that invokes given runnables when all constraints of a given set are satisfied at the same time.
 * The executor is created by calling {@link #on}, the constraints are specified by chained calls. For example, to invoke
 * some action when all documents are committed and indices are available, one can use
 * {@code AppExecutor.on(AppExecutorUtil.getAppExecutorService()).inReadAction(project).inSmartMode(project)}.
 */

public interface AppExecutor extends BaseAppExecutor<AppExecutor> {

  /**
   * Creates constrained executor from provided executor
   */
  @NotNull
  static AppExecutor on(@NotNull Executor executor) {
    return AsyncExecutionService.getService().createExecutor(executor);
  }
}
