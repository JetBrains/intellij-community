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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

public class ApplicationUtil {
  // throws exception if can't grab read action right now
  public static <T> T tryRunReadAction(@NotNull final Computable<T> computable) throws CannotRunReadActionException {
    final Ref<T> result = new Ref<T>();
    if (((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(new Runnable() {
      @Override
      public void run() {
        result.set(computable.compute());
      }
    })) {
      return result.get();
    }
    throw new CannotRunReadActionException();
  }

  public static void tryRunReadAction(@NotNull final Runnable computable) throws CannotRunReadActionException {
    if (!((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(computable)) {
      throw new CannotRunReadActionException();
    }
  }

  public static class CannotRunReadActionException extends RuntimeException{
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }
}
