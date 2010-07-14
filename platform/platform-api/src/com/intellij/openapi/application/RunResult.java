/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


public class RunResult<T> extends Result<T> {

  private BaseActionRunnable<T> myActionRunnable;

  private Throwable myThrowable;

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
        if (throwable instanceof RuntimeException) throw (RuntimeException)throwable;
        if (throwable instanceof Error) throw (Error)throwable;
        else throw new RuntimeException(myThrowable);
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

  public RunResult<T> throwException() {
    if (hasException()) {
      if (myThrowable instanceof RuntimeException) {
        throw (RuntimeException)myThrowable;
      }
      if (myThrowable instanceof Error) {
        throw (Error)myThrowable;
      }

      throw new RuntimeException(myThrowable);
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
