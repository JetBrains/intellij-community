/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.Nullable;


public class RunResult<T> extends Result<T> {

  private BaseActionRunnable<T> myActionRunnable;

  protected Throwable myThrowable;

  protected RunResult() {
  }

  public RunResult(BaseActionRunnable<T> action) {
    myActionRunnable = action;
  }

  public RunResult<T> run() {
    try {
      myActionRunnable.run(this);
    } catch (ProcessCanceledException e) {
      throw e; // this exception may occur from time to time and it shouldn't be catched
    } catch (Throwable throwable) {
      myThrowable = throwable;
      if (!myActionRunnable.isSilentExecution()) {
        if (myThrowable instanceof Error) throw (Error)myThrowable;
        else if (myThrowable instanceof RuntimeException) throw (RuntimeException)myThrowable; 
        else throw new Error(myThrowable);
      }
    }
    finally {
      myActionRunnable = null;
    }

    return this;
  }

  public T getResultObject() {
    return myResult;
  }

  public RunResult logException(Logger logger) {
    if (hasException()) {
      logger.error(myThrowable);
    }

    return this;
  }

  public void throwException() throws Throwable {
    if (hasException()) {
      throw myThrowable;
    }
  }

  public boolean hasException() {
    return myThrowable != null;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }

  public void setThrowable(Throwable throwable) {
    myThrowable = throwable;
  }

}
