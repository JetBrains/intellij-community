// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Runs a test with only the production methods of the daemon, as {@link ProductionLightDaemonAnalyzerTestCase} does.
 * The class does not extend a JUnit 3 class, so a JUnit 5 test can call it.
 */
@TestOnly
public final class ProductionDaemonTestUtil {
  private static final Logger LOG = Logger.getInstance(ProductionDaemonTestUtil.class);

  private ProductionDaemonTestUtil() {
  }

  /**
   * Runs {@code testRunnable} with the daemon update by timer on, a zero reparse delay and the daemon debug mode.
   * {@link CodeInsightTestFixtureImpl#instantiateAndRun} fails in {@code testRunnable}, so the test waits for the daemon itself,
   * for example with {@link TestDaemonCodeAnalyzerImpl#waitHighlighting}.
   * Call it on the thread that {@code testRunnable} needs.
   */
  public static void runTestInProduction(boolean isStressTest,
                                         @NotNull DaemonCodeAnalyzerImpl codeAnalyzer,
                                         @NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean wasUpdateByTimerEnabled = codeAnalyzer.isUpdateByTimerEnabled();
    try {
      if (!wasUpdateByTimerEnabled) {
        codeAnalyzer.setUpdateByTimerEnabled(true);
      }
      TestDaemonCodeAnalyzerImpl.runWithDaemonLoggerTraceLevel(isStressTest, ()->
      ((CoreProgressManager)ProgressManager.getInstance()).suppressAllDeprioritizationsDuringLongTestsExecutionIn(()-> {
      DaemonProgressIndicator.runInDebugMode(() ->
      CodeInsightTestFixtureImpl.disableInstantiateAndRunIn(() ->
      TestDaemonCodeAnalyzerImpl.runWithReparseDelay(0, ()-> {
        try {
          testRunnable.run();
        }
        catch (Throwable e) {
          LOG.info(e); // to make the exact moment visible in the test log
          throw e;
        }
      })));
      return null;
      }));
    }
    finally {
      if (!wasUpdateByTimerEnabled) {
        codeAnalyzer.setUpdateByTimerEnabled(false);
      }
    }
  }
}
