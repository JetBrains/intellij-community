/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;


/**
 * @deprecated Use {@link WriteAction}
 */
@Deprecated
public abstract class ActionRunner {
  /**
   * @deprecated use {@link WriteAction#run(ThrowableRunnable)} or {@link WriteAction#compute(ThrowableComputable)} instead
   */
  @Deprecated
  public static void runInsideWriteAction(@NotNull final InterruptibleRunnable runnable) throws Exception {
    WriteAction.computeAndWait(() -> {
      runnable.run();
      return null;
    });
  }

  /**
   * @deprecated use {@link WriteAction#run(ThrowableRunnable)} or {@link WriteAction#compute(ThrowableComputable)} instead
   */
  @Deprecated(forRemoval = true)
  public static <T> T runInsideWriteAction(@NotNull final InterruptibleRunnableWithResult<T> runnable) throws Exception {
    return WriteAction.computeAndWait(() -> runnable.run());
  }

  /**
   * @deprecated use {@link ReadAction#run(ThrowableRunnable)} or {@link ReadAction#compute(ThrowableComputable)} instead
   */
  @Deprecated(forRemoval = true)
  public static void runInsideReadAction(@NotNull final InterruptibleRunnable runnable) throws Exception {
    ReadAction.run(() -> runnable.run());
  }

  /**
   * @deprecated obsolete API
   */
  @Deprecated
  public interface InterruptibleRunnable {
    void run() throws Exception;
  }

  /**
   * @deprecated obsolete API
   */
  @Deprecated(forRemoval = true)
  public interface InterruptibleRunnableWithResult<T> {
    T run() throws Exception;
  }
}