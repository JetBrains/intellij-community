// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class BaseActionRunnable<T> {
  private boolean mySilentExecution;

  public boolean isSilentExecution() {
    return mySilentExecution;
  }

  protected abstract void run(@NotNull Result<T> result) throws Throwable;

  /**
   * @deprecated use {@link ReadAction#run(ThrowableRunnable)}
   * or {@link WriteAction#run(ThrowableRunnable)}
   * or {@link com.intellij.openapi.command.WriteCommandAction#runWriteCommandAction(Project, Runnable)}
   * or similar
   */
  @Deprecated
  @NotNull
  public abstract RunResult<T> execute();

  /**
   * Same as {@link #execute()}, but does not log an error if an exception occurs.
   * @deprecated use {@link ReadAction#run(ThrowableRunnable)} or  {@link WriteAction#run(ThrowableRunnable)} instead
   */
  @Deprecated
  @NotNull
  public final RunResult<T> executeSilently() {
    mySilentExecution = true;
    return execute();
  }
}