/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class WriteAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.WriteAction");

  @NotNull
  @Override
  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<>(this);

    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      AccessToken token = start(getClass());
      try {
        result.run();
      }
      finally {
        token.finish();
      }
      return result;
    }

    if (application.isReadAccessAllowed()) {
      LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
    }

    TransactionGuard.getInstance().submitTransactionAndWait(() -> {
      AccessToken token = start(getClass());
      try {
        result.run();
      }
      finally {
        token.finish();
      }
    });

    if (!isSilentExecution()) {
      result.throwException();
    }

    return result;
  }

  /**
   * @see #run(ThrowableRunnable)
   * @see #compute(ThrowableComputable)
   */
  @Deprecated
  @NotNull
  public static AccessToken start() {
    // get useful information about the write action
    return start(ObjectUtils.notNull(ReflectionUtil.getGrandCallerClass(), WriteAction.class));
  }

  /**
   * @see #run(ThrowableRunnable)
   * @see #compute(ThrowableComputable)
   */
  @Deprecated
  @NotNull
  private static AccessToken start(@NotNull Class clazz) {
    return ApplicationManager.getApplication().acquireWriteActionLock(clazz);
  }

  public static <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E {
    AccessToken token = start();
    try {
      action.run();
    }
    finally {
      token.finish();
    }
  }

  public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> action) throws E {
    AccessToken token = start();
    try {
      return action.compute();
    }
    finally {
      token.finish();
    }
  }
}