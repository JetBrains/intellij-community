// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.internal.statistic.FUCollectorTestCase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DaemonFusReporterTest extends BasePlatformTestCase {
  public void testDaemonFUSIsReportedAfterTyping() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class X {
        void f() {<caret>
        }
      }""");

    boolean highlightingCompletedExists = hasFUSHighlightFinishedEventsAfter(() -> assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR)));
    assertTrue("There must be an event with highlighting_completed=true", highlightingCompletedExists);

    boolean highlightingCompletedExistsAfterTyping = hasFUSHighlightFinishedEventsAfter(() -> {
      myFixture.type("xxx//");
      assertNotEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    });
    assertTrue("There must be an event with highlighting_completed=true after typing", highlightingCompletedExistsAfterTyping);

    boolean highlightingIsReportedAfterMeaninglessRestart = hasFUSHighlightFinishedEventsAfter(() -> {
      DaemonCodeAnalyzerEx.getInstanceEx(myFixture.getProject()).restart(getTestName(false));
      assertNotEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    });
    assertFalse("highlighting_completed=true should not be reported after meaningless restart", highlightingIsReportedAfterMeaninglessRestart);
  }

  private static boolean hasFUSHighlightFinishedEventsAfter(Runnable runnable) {
    List<LogEvent> events = collectFUSEvents(runnable);
    return events.stream()
      .filter(e -> e.getGroup().getId().equals(DaemonFusCollector.GROUP.getId()))
      .filter(e -> e.getEvent().getId().equals(DaemonFusCollector.FINISHED.getEventId()))
      .anyMatch(e -> e.getEvent().getData().get(DaemonFusCollector.HIGHLIGHTING_COMPLETED.getName()) == Boolean.TRUE);
  }

  @NotNull
  private static List<LogEvent> collectFUSEvents(Runnable action) {
    Disposable disposable = Disposer.newDisposable();
    try {
      return FUCollectorTestCase.INSTANCE.collectLogEvents(disposable, () -> {
        action.run();
        DaemonFusReporter.Companion.drain();
        return Unit.INSTANCE;
      });
    }
    finally {
      Disposer.dispose(disposable);
    }
  }
}

