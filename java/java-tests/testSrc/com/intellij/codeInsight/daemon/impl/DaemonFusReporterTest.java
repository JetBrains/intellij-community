// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.internal.statistic.FUCollectorTestCase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DaemonFusReporterTest extends BasePlatformTestCase {
  public void testDaemonFUSIsReportedAfterTyping() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class X {
        void f() {<caret>
        }
      }""");
    List<LogEvent> events = collectFUSEvents(() -> assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR)));
    boolean entireFileHighlightedExists = events.stream()
      .filter(e -> e.getGroup().getId().equals(DaemonFusCollector.GROUP.getId()))
      .filter(e -> e.getEvent().getId().equals(DaemonFusCollector.FINISHED.getEventId()))
      .filter(e -> e.getEvent().getData().get(DaemonFusCollector.CANCELED.getName()) == Boolean.FALSE)
      .anyMatch(e -> e.getEvent().getData().get(DaemonFusCollector.ENTIRE_FILE_HIGHLIGHTED.getName()) == Boolean.TRUE);
    assertTrue("There must be an event with entireFileHighlighted=true", entireFileHighlightedExists);
    List<LogEvent> events2 = collectFUSEvents(() -> {
      myFixture.type("xxx//");
      assertNotEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    });
    boolean daemonSuccessfullyFinishedExists = events2.stream()
      .filter(e -> e.getGroup().getId().equals(DaemonFusCollector.GROUP.getId()))
      .filter(e -> e.getEvent().getId().equals(DaemonFusCollector.FINISHED.getEventId()))
      .anyMatch(e -> e.getEvent().getData().get(DaemonFusCollector.CANCELED.getName()) == Boolean.FALSE);
    assertTrue("There must be an event with canceled=false after typing", daemonSuccessfullyFinishedExists);

    boolean entireFileHighlightedIsFalseForCodeBlockModifications = events2.stream()
      .filter(e -> e.getGroup().getId().equals(DaemonFusCollector.GROUP.getId()))
      .filter(e -> e.getEvent().getId().equals(DaemonFusCollector.FINISHED.getEventId()))
      .allMatch(e -> e.getEvent().getData().get(DaemonFusCollector.ENTIRE_FILE_HIGHLIGHTED.getName()) == Boolean.FALSE);
    assertTrue("entireFileHighlighted must be false for inside code-block modifications", entireFileHighlightedIsFalseForCodeBlockModifications);
  }

  @NotNull
  private static List<LogEvent> collectFUSEvents(Runnable action) {
    Disposable disposable = Disposer.newDisposable();
    try {
      return FUCollectorTestCase.INSTANCE.collectLogEvents(disposable, () -> { action.run(); return null; });
    }
    finally {
      Disposer.dispose(disposable);
    }
  }
}

