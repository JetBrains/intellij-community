// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProductionLightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InspectionLIfeCycleTest extends ProductionLightDaemonAnalyzerTestCase {
  public void testInspectionFinishedCalledOnce() {
    String text = """
      class LQF {
          int f;
          public void me() {
              <caret>
          }
      }""";
    configureFromFileText("x.java", text);

    final AtomicInteger startedCount = new AtomicInteger();
    final AtomicInteger finishedCount = new AtomicInteger();
    final Key<Object> KEY = Key.create("just key");

    LocalInspectionTool tool = new LocalInspectionTool() {
      @Nls
      @NotNull
      @Override
      public String getGroupDisplayName() {
        return "fegna";
      }

      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return getGroupDisplayName();
      }

      @NotNull
      @Override
      public String getShortName() {
        return getGroupDisplayName();
      }

      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
        };
      }

      @Override
      public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
        startedCount.incrementAndGet();
        session.putUserData(KEY, session);
      }

      @Override
      public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
        finishedCount.incrementAndGet();
        assertEmpty(problemsHolder.getResults());
        assertSame(session, session.getUserData(KEY));
      }
    };
    enableInspectionTool(tool);

    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getFile(), HighlightSeverity.ERROR);
    assertEmpty(infos);

    assertEquals(1, startedCount.get());
    assertEquals(1, finishedCount.get());
  }
}
