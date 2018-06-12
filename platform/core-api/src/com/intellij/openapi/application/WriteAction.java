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
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public abstract class WriteAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.WriteAction");

  /**
   * @deprecated use {@link #run(ThrowableRunnable)}
   * or {@link #compute(ThrowableComputable)}
   * or (if really desperate) {@link #computeAndWait(ThrowableComputable)} instead
   */
  @Deprecated
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
   * @deprecated Use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
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
   * @deprecated Use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   * @see #run(ThrowableRunnable)
   * @see #compute(ThrowableComputable)
   */
  @Deprecated
  @NotNull
  private static AccessToken start(@NotNull Class clazz) {
    return ApplicationManager.getApplication().acquireWriteActionLock(clazz);
  }

  /**
   * Executes {@code action} inside write action.
   * Must be called from the EDT.
   */
  public static <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E {
    AccessToken token = start();
    try {
      action.run();
    }
    finally {
      token.finish();
    }
  }

  /**
   * Executes {@code action} inside write action and returns the result.
   * Must be called from the EDT.
   */
  public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> action) throws E {
    AccessToken token = start();
    try {
      return action.compute();
    }
    finally {
      token.finish();
    }
  }

  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  @Override
  protected abstract void run(@NotNull Result<T> result) throws Throwable;

  /**
   * Executes {@code action} inside write action.
   * If called from outside the EDT, transfers control to the EDT first, executes write action there and waits for the execution end.
   * <br/><span color=red>CAUTION</span>: if called from outside EDT, please be aware of possible deadlocks (e.g. when EDT is busy)
   * or invalid data (e.g. when something is changed during control transferred to EDT and back).
   * <br/>Instead, please use {@link #run(ThrowableRunnable)}.
   */
  public static <E extends Throwable> void runAndWait(@NotNull ThrowableRunnable<E> action) throws E {
    computeAndWait(()->{
      action.run();
      return null;
    });
  }
  /**
   * Executes {@code action} inside write action.
   * If called from outside the EDT, transfers control to the EDT first, executes write action there and waits for the execution end.
   * <br/><span color=red>CAUTION</span>: if called from outside EDT, please be aware of possible deadlocks (e.g. when EDT is busy)
   * or invalid data (e.g. when something is changed during control transferred to EDT and back).
   * <br/>Instead, please use {@link #compute(ThrowableComputable)}.
   */
  public static <T, E extends Throwable> T computeAndWait(@NotNull ThrowableComputable<T, E> action) throws E {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      AccessToken token = start(action.getClass());
      try {
        return action.compute();
      }
      finally {
        token.finish();
      }
    }

    if (application.isReadAccessAllowed()) {
      LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
    }
    final AtomicReference<T> result = new AtomicReference<>();
    final AtomicReference<Throwable> exception = new AtomicReference<>();
    TransactionGuard.getInstance().submitTransactionAndWait(() -> {
      AccessToken token = start(action.getClass());
      try {
        result.set(action.compute());
      }
      catch (Throwable e) {
        exception.set(e);
      }
      finally {
        token.finish();
      }
    });

    Throwable t = exception.get();
    if (t != null) {
      t.addSuppressed(new RuntimeException()); // preserve the calling thread stacktrace
      ExceptionUtil.rethrowUnchecked(t);
      //noinspection unchecked
      throw (E)t;
    }
    return result.get();
  }
}