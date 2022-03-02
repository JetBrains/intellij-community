// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link WriteAction#run(ThrowableRunnable)} or {@link ReadAction#run(ThrowableRunnable)} or similar method instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public abstract class BaseActionRunnable<T> {
  protected abstract void run(@NotNull Result<? super T> result) throws Throwable;

  /**
   * @deprecated use {@link ReadAction#run(ThrowableRunnable)}
   * or {@link WriteAction#run(ThrowableRunnable)}
   * or {@link com.intellij.openapi.command.WriteCommandAction#runWriteCommandAction(Project, Runnable)}
   * or similar
   */
  @Deprecated
  @NotNull
  public abstract RunResult<T> execute();
}