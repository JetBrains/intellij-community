// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerTestCase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests that the first daemon pass of a project starts without the autoreparse delay and that later restarts honor it.
 */
public class DaemonFirstPassDelayTest extends ProductionDaemonAnalyzerTestCase {
  private static final int REPARSE_DELAY_MS = 10_000;
  private static final int FIRST_PASS_START_TIMEOUT_MS = 5_000;
  private static final int LATER_RESTART_QUIET_MS = 1_000;

  public void testFirstPassStartsWithoutReparseDelayAndLaterRestartsHonorIt() {
    TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }
      });

      // phase 1: the first pass starts long before REPARSE_DELAY_MS
      configureByText(PlainTextFileType.INSTANCE, "text");
      long start = System.currentTimeMillis();
      while (starts.get() == 0) {
        assertTrue("The first pass must start without the autoreparse delay", System.currentTimeMillis() - start < FIRST_PASS_START_TIMEOUT_MS);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        TimeoutUtil.sleep(10);
      }
      // let one pass finish, so the daemon switches to the configured delay
      myTestDaemonCodeAnalyzer.waitHighlightingSurviveCancellations(getFile(), HighlightSeverity.INFORMATION);

      // phase 2: a later restart waits for the delay
      int startsBefore = starts.get();
      myDaemonCodeAnalyzer.restart(getTestName(false));
      long restartTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - restartTime < LATER_RESTART_QUIET_MS) {
        TimeoutUtil.sleep(100);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals("A later restart must wait for the autoreparse delay", startsBefore, starts.get());
        assertFalse("The daemon must not run before the autoreparse delay", myDaemonCodeAnalyzer.isRunning());
      }
      assertTrue("The restart must stay pending until the autoreparse delay expires", myDaemonCodeAnalyzer.isRunningOrPending());
    });
  }
}
