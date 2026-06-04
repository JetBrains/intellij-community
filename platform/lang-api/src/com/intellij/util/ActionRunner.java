// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;


/**
 * @deprecated Use {@link WriteAction}
 */
@Deprecated(forRemoval = true)
public abstract class ActionRunner {
  /**
   * @deprecated use {@link WriteAction#run(ThrowableRunnable)} or {@link WriteAction#compute(ThrowableComputable)} instead
   */
  @Deprecated(forRemoval = true)
  public static void runInsideWriteAction(final @NotNull InterruptibleRunnable runnable) throws Exception {
    WriteAction.computeAndWait(() -> {
      runnable.run();
      return null;
    });
  }

  /**
   * @deprecated obsolete API
   */
  @Deprecated(forRemoval = true)
  public interface InterruptibleRunnable {
    void run() throws Exception;
  }
}