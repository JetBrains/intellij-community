// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

public final class RunResult<T> extends Result<T> {
  private BaseActionRunnable<T> myActionRunnable;
  private Throwable myThrowable;

  private RunResult() { }

  public RunResult(@NotNull BaseActionRunnable<T> action) {
    myActionRunnable = action;
  }

  @NotNull
  public RunResult<T> run() {
    try {
      myActionRunnable.run(this);
    }
    catch (ProcessCanceledException e) {
      throw e; // this exception may occur from time to time and it shouldn't be caught
    }
    catch (Throwable t) {
      myThrowable = t;
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    finally {
      myActionRunnable = null;
    }

    return this;
  }

  public T getResultObject() {
    return myResult;
  }

  @NotNull
  public RunResult logException(Logger logger) {
    if (myThrowable != null) {
      logger.error(myThrowable);
    }

    return this;
  }

  @NotNull
  public RunResult<T> throwException() throws RuntimeException, Error {
    if (myThrowable != null) {
      ExceptionUtil.rethrowAllAsUnchecked(myThrowable);
    }

    return this;
  }

  public boolean hasException() {
    return myThrowable != null;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }

  public void setThrowable(Exception throwable) {
    myThrowable = throwable;
  }
}
