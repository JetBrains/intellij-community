// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InspectionLIfeCycleTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, () -> List.of(), LightDaemonAnalyzerOptions.DEFAULT.withProductionDaemon());

  @Test
  public void testInspectionFinishedCalledOnce() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      String text = """
        class LQF {
            int f;
            public void me() {
                <caret>
            }
        }""";
      daemon.configureFromFileText("x.java", text);

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
          assertTrue(problemsHolder.getResults().isEmpty());
          assertSame(session, session.getUserData(KEY));
        }
      };
      daemon.getFixture().enableInspections(tool);

      List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.ERROR);
      assertTrue(infos.isEmpty(), infos.toString());

      assertEquals(1, startedCount.get());
      assertEquals(1, finishedCount.get());
    });
  }
}
