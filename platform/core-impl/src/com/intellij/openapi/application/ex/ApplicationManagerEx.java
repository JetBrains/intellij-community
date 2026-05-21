// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class ApplicationManagerEx extends ApplicationManager {
  public static final String IS_INTERNAL_PROPERTY = "idea.is.internal";

  private static volatile boolean inStressTest;

  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx)ourApplication;
  }

  public static boolean isInStressTest() {
    return inStressTest;
  }

  public static boolean isInIntegrationTest() {
    return Boolean.getBoolean("idea.is.integration.test");
  }

  /**
   * @deprecated use {@link #runInStressTest} instead
   */
  @TestOnly
  @ApiStatus.Internal
  @Deprecated
  public static void setInStressTest(boolean value) {
    inStressTest = value;
    IndexDebugProperties.IS_IN_STRESS_TESTS = value;
    Logger.setInStressTest(value);
  }

  /// Run the supplied `runnable` with [#isInStressTest()]=`value` and restore the original [#isInStressTest()] after the execution.
  @TestOnly
  @ApiStatus.Internal
  public static <E extends Throwable> void runInStressTest(boolean value, @NotNull ThrowableRunnable<E> runnable) throws E {
    assert getApplicationEx()==null||getApplicationEx().isUnitTestMode() : "must use in test mode only";
    boolean old = inStressTest;
    inStressTest = value;
    IndexDebugProperties.IS_IN_STRESS_TESTS = value;
    Logger.setInStressTest(value);
    try {
      runnable.run();
    }
    finally {
      inStressTest = old;
      IndexDebugProperties.IS_IN_STRESS_TESTS = old;
      Logger.setInStressTest(old);
    }
  }
}
