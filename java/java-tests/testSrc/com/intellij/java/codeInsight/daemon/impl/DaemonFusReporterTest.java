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
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("KotlinInternalInJava")
@TestApplication
public class DaemonFusReporterTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testDaemonFUSIsReportedAfterTyping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class X {
        void f() {<caret>
        }
      }""");

    boolean highlightingCompletedExists = hasFUSHighlightFinishedEventsAfter(() -> assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty());
    assertTrue(highlightingCompletedExists, "There must be an event with highlighting_completed=true");

    boolean highlightingCompletedExistsAfterTyping = hasFUSHighlightFinishedEventsAfter(() -> {
      myFixture.type("xxx//");
      assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
    });
    assertTrue(highlightingCompletedExistsAfterTyping, "There must be an event with highlighting_completed=true after typing");

    boolean highlightingIsReportedAfterMeaninglessRestart = hasFUSHighlightFinishedEventsAfter(() -> {
      DaemonCodeAnalyzerEx.getInstanceEx(myFixture.getProject()).restart(myFixture.getTestName(false));
      assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
    });
    assertFalse(highlightingIsReportedAfterMeaninglessRestart, "highlighting_completed=true should not be reported after meaningless restart");
  }

  @Test
  public void testDaemonFUSIsReportedAfterDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {}");
    var stamp = myFixture.getEditor().getDocument().getModificationStamp();
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, myFixture.getTestRootDisposable());

    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      var events = collectFUSEvents(() -> assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty());
      assertTrue(hasCompletedHighlightingEvent(events, true), "Highlighting in dumb mode must be reported");
    });

    var events = collectFUSEvents(() -> assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty());
    assertTrue(hasCompletedHighlightingEvent(events, false), "Highlighting after dumb mode must be reported");
    assertEquals(stamp, myFixture.getEditor().getDocument().getModificationStamp());

    assertFalse(hasFUSHighlightFinishedEventsAfter(() -> {
      DaemonCodeAnalyzerEx.getInstanceEx(myFixture.getProject()).restart(myFixture.getTestName(false));
      assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty();
    }), "A restart without changes must not be reported");
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
