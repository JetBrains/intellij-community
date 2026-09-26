// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A {@link LightDaemonAnalyzerTestCase} which uses only production methods of the daemon and waits for its completion via e.g.
 * {@link com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl#waitForDaemonToFinish} methods
 * and prohibits explicitly manipulating daemon state via e.g. {@link CodeInsightTestFixtureImpl#instantiateAndRun}
 */
public abstract class ProductionLightDaemonAnalyzerTestCase extends LightDaemonAnalyzerTestCase {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    runTestInProduction(isStressTest(), myDaemonCodeAnalyzer, () -> super.runTestRunnable(testRunnable));
  }

  @Override
  protected final @NotNull @Unmodifiable List<HighlightInfo> doHighlighting() {
    return myTestDaemonCodeAnalyzer.waitHighlighting(getFile(), HighlightInfoType.SYMBOL_TYPE_SEVERITY);
  }

  private String myMLProperty;
  @Override
  protected void setUp() throws Exception {
    myMLProperty = System.setProperty("intellij.ml.llm.embeddings.start.indexing.on.project.open", "false");
    super.setUp();
  }
  @Override
  protected void tearDown() throws Exception {
    if (myMLProperty == null) {
      System.clearProperty("intellij.ml.llm.embeddings.start.indexing.on.project.open");
    }
    else {
      System.setProperty("intellij.ml.llm.embeddings.start.indexing.on.project.open", myMLProperty);
    }
    super.tearDown();
  }

  public static void runTestInProduction(boolean isStressTest, @NotNull DaemonCodeAnalyzerImpl codeAnalyzer, @NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean wasUpdateByTimerEnabled = codeAnalyzer.isUpdateByTimerEnabled();
    Disposable disposable = Disposer.newDisposable();
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
      Disposer.dispose(disposable);
    }
  }
}