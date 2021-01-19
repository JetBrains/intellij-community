// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class SlowOperations {

  private static final Logger LOG = Logger.getInstance(SlowOperations.class);
  private static final Set<String> ourReportedTraces = new HashSet<>();
  private static boolean ourAllowedFlag = false;

  private SlowOperations() {}

  public static void assertSlowOperationsAreAllowed() {
    if (Registry.is("ide.enable.slow.operations.in.edt")) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (
      !application.isUnitTestMode() &&
      application.isDispatchThread() &&
      !ourAllowedFlag &&
      ourReportedTraces.add(ExceptionUtil.currentStackTrace())
    ) {
      LOG.error("Slow operations are prohibited in the EDT");
    }
  }

  public static <T, E extends Throwable> T allowSlowOperations(@NotNull ThrowableComputable<T, E> computable) throws E {
    if (!ApplicationManager.getApplication().isDispatchThread() || ourAllowedFlag) {
      return computable.compute();
    }
    ourAllowedFlag = true;
    try {
      return computable.compute();
    }
    finally {
      ourAllowedFlag = false;
    }
  }

  public static <E extends Throwable> void allowSlowOperations(@NotNull ThrowableRunnable<E> runnable) throws E {
    allowSlowOperations(() -> {
      runnable.run();
      return null;
    });
  }
}
