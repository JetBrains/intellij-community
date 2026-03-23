// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightInfoUpdaterImpl;
import com.intellij.codeInsight.daemon.impl.PassExecutorService;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.IntervalTreeImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

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

  public static void runTestInProduction(boolean isStressTest, @NotNull DaemonCodeAnalyzerImpl codeAnalyzer, @NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean wasUpdateByTimerEnabled = codeAnalyzer.isUpdateByTimerEnabled();
    try {
      if (!wasUpdateByTimerEnabled) {
        codeAnalyzer.setUpdateByTimerEnabled(true);
      }
      runWithDaemonLoggerTraceLevel(isStressTest, ()->
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
  // set daemon loggers to TRACE log level, execute runnable, and restore the level to not freak out other tests
  private static <T extends Throwable> void withLoggerTraceLevel(boolean isStressTest, @NotNull List<String> classNames, @NotNull ThrowableRunnable<T> runnable) throws T {
    Map<JulLogger, LogLevel> oldLevels = ContainerUtil.map2Map(classNames, className -> {
      JulLogger logger;
      try {
        logger = (JulLogger)ReflectionUtil.getField(Class.forName(className), null, Logger.class, "LOG");
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      LogLevel oldLevel = logger.getLevel();
      Pair<JulLogger, LogLevel> pair = Pair.create(logger, oldLevel);
      if (!isStressTest) {
        logger.setLevel(LogLevel.TRACE);
      }
      return pair;
    });
    try {
      runnable.run();
    }
    finally {
      for (Map.Entry<JulLogger, LogLevel> entry : oldLevels.entrySet()) {
        JulLogger logger = entry.getKey();
        LogLevel oldLevel = entry.getValue();
        logger.setLevel(oldLevel);
      }
    }
  }
  private static <T extends Throwable> void runWithDaemonLoggerTraceLevel(boolean isStressTest, @NotNull ThrowableRunnable<T> runnable) throws T {
    withLoggerTraceLevel(isStressTest, List.of(
      BackgroundUpdateHighlightersUtil.class.getName(),
      DaemonCodeAnalyzerImpl.class.getName(),
      DaemonProgressIndicator.class.getName(),
      FileStatusMap.class.getName(),
      GeneralHighlightingPass.class.getName(),
      HighlightInfoUpdaterImpl.class.getName(),
      IntervalTreeImpl.class.getName(),
      PassExecutorService.class.getName(),
      "com.intellij.openapi.editor.impl.RangeHighlighterImpl",
      UpdateHighlightersUtil.class.getName()
    ), runnable);
  }
}