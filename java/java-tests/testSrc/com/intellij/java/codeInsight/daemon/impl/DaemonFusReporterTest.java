// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonFusCollector;
import com.intellij.codeInsight.daemon.impl.DaemonFusReporter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.internal.statistic.FUCollectorTestCase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("KotlinInternalInJava")
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

  public void testDaemonFUSIsReportedAfterDumbMode() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {}");
    var stamp = myFixture.getEditor().getDocument().getModificationStamp();
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, getTestRootDisposable());

    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      var events = collectFUSEvents(() -> assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR)));
      assertTrue("Highlighting in dumb mode must be reported", hasCompletedHighlightingEvent(events, true));
    });

    var events = collectFUSEvents(() -> assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR)));
    assertTrue("Highlighting after dumb mode must be reported", hasCompletedHighlightingEvent(events, false));
    assertEquals(stamp, myFixture.getEditor().getDocument().getModificationStamp());

    assertFalse("A restart without changes must not be reported", hasFUSHighlightFinishedEventsAfter(() -> {
      DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart(getTestName(false));
      assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    }));
  }

  private static boolean hasCompletedHighlightingEvent(List<? extends LogEvent> events, boolean dumbMode) {
    return events.stream()
      .filter(e -> e.getGroup().getId().equals(DaemonFusCollector.GROUP.getId()))
      .filter(e -> e.getEvent().getId().equals(DaemonFusCollector.FINISHED.getEventId()))
      .filter(e -> e.getEvent().getData().get(DaemonFusCollector.DUMB_MODE.getName()) == Boolean.valueOf(dumbMode))
      .anyMatch(e -> e.getEvent().getData().get(DaemonFusCollector.HIGHLIGHTING_COMPLETED.getName()) == Boolean.TRUE);
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
