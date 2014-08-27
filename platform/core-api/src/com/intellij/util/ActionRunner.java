/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;


public abstract class ActionRunner {
  public static  void runInsideWriteAction(@NotNull final InterruptibleRunnable runnable) throws Exception {
    RunResult result = new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        runnable.run();
      }
    }.execute();
    if (result.getThrowable() instanceof Exception) throw (Exception)result.getThrowable();
    result.throwException();
  }

  public static <T> T runInsideWriteAction(@NotNull final InterruptibleRunnableWithResult<T> runnable) throws Exception {
    RunResult<T> result = new WriteAction<T>() {
      @Override
      protected void run(@NotNull Result<T> result) throws Throwable {
        result.setResult(runnable.run());
      }
    }.execute();
    if (result.getThrowable() instanceof Exception) throw (Exception)result.getThrowable();
    return result.throwException().getResultObject();
  }

  public static void runInsideReadAction(@NotNull final InterruptibleRunnable runnable) throws Exception {
    ApplicationManager.getApplication().runReadAction(new ThrowableComputable<Void, Exception>() {
      @Override
      public Void compute() throws Exception {
        runnable.run();
        return null;
      }
    });
  }

  public interface InterruptibleRunnable {
    void run() throws Exception;
  }
  public interface InterruptibleRunnableWithResult<T> {
    T run() throws Exception;
  }
}