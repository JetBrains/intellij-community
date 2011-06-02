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

import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.Nullable;

public abstract class WriteAction<T> extends BaseActionRunnable<T> {
  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<T>(this);

    if (canWriteNow()) {
      result.run();
      return result;
    }

    try {
      GuiUtils.runOrInvokeAndWait(new Runnable() {
        public void run() {
          final AccessToken accessToken = start();
          try {
            result.run();
          } finally {
            accessToken.finish();
          }
        }
      });
    }
    catch (Exception e) {
      if (isSilentExecution()) {
        result.setThrowable(e);
      }
      else {
        if (e instanceof RuntimeException) throw (RuntimeException)e;
        throw new Error(e);
      }
    }
    return result;
  }

  public static AccessToken start() {
    return start(null);
  }

  public static AccessToken start(@Nullable Class clazz) {
    return ApplicationManager.getApplication().acquireWriteActionLock(clazz);
  }
}
