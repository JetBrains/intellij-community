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
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

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

    boolean dispatchThread = application.isDispatchThread();
    if (!dispatchThread && application.isReadAccessAllowed()) {
      LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
    }

    application.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        AccessToken token = application.acquireWriteActionLock(WriteAction.this.getClass());
        try {
          result.run();
        }
        finally {
          token.finish();
        }
      }
    }, ModalityState.defaultModalityState());

    result.throwException();
    return result;
  }

  @NotNull
  public static AccessToken start() {
    // get useful information about the write action
    Class aClass = ObjectUtils.notNull(ReflectionUtil.getGrandCallerClass(), WriteAction.class);
    return start(aClass);
  }

  @NotNull
  public static AccessToken start(@NotNull Class clazz) {
    return ApplicationManager.getApplication().acquireWriteActionLock(clazz);
  }
}
