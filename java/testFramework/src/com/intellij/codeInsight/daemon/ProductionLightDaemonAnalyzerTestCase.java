// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
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
    runTestInProduction(myDaemonCodeAnalyzer, () -> super.runTestRunnable(testRunnable));
  }

  @Override
  protected final @NotNull @Unmodifiable List<HighlightInfo> doHighlighting() {
    return myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightInfoType.SYMBOL_TYPE_SEVERITY);
  }

  public static void runTestInProduction(@NotNull DaemonCodeAnalyzerImpl codeAnalyzer, @NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean wasUpdateByTimerEnabled = codeAnalyzer.isUpdateByTimerEnabled();
    try {
      if (!wasUpdateByTimerEnabled) {
        codeAnalyzer.setUpdateByTimerEnabled(true);
      }
      DaemonProgressIndicator.runInDebugMode(() ->
      CodeInsightTestFixtureImpl.disableInstantiateAndRunIn(() ->
      TestDaemonCodeAnalyzerImpl.runWithReparseDelay(0, testRunnable)));
    }
    finally {
      if (!wasUpdateByTimerEnabled) {
        codeAnalyzer.setUpdateByTimerEnabled(false);
      }
    }
  }
}