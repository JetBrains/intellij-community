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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class WriteAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.WriteAction");

  @NotNull
  @Override
  @SuppressWarnings("InstanceofCatchParameter")
  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<T>(this);

    final Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed()) {
      result.run();
      return result;
    }

    try {
      boolean dispatchThread = application.isDispatchThread();
      if (!dispatchThread && application.isReadAccessAllowed()) {
        LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
      }
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              result.run();
            }
          });
        }
      };
      if (dispatchThread) {
        runnable.run();
      }
      else if (application.isReadAccessAllowed()) {
        LOG.error("Calling write action from read-action leads to deadlock.");
      }
      else {
        SwingUtilities.invokeAndWait(runnable);
      }
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

  @NotNull
  public static AccessToken start() {
    return start(null);
  }

  @NotNull
  public static AccessToken start(@Nullable Class clazz) {
    return ApplicationManager.getApplication().acquireWriteActionLock(clazz);
  }
}
