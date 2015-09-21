/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

public class RunResult<T> extends Result<T> {
  private BaseActionRunnable<T> myActionRunnable;
  private Throwable myThrowable;

  protected RunResult() { }

  public RunResult(@NotNull BaseActionRunnable<T> action) {
    myActionRunnable = action;
  }

  public RunResult<T> run() {
    try {
      myActionRunnable.run(this);
    }
    catch (ProcessCanceledException e) {
      throw e; // this exception may occur from time to time and it shouldn't be caught
    }
    catch (Throwable t) {
      myThrowable = t;
      if (!myActionRunnable.isSilentExecution()) {
        ExceptionUtil.rethrowAllAsUnchecked(t);
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
