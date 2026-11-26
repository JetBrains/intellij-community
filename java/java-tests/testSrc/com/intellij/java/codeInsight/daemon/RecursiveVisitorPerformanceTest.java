// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SkipSlowTestLocally
@PerformanceUnitTest
@StressTestApplication
public class RecursiveVisitorPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testHugeConcatenationVisitingPerformance() throws IncorrectOperationException {
    final int N = 20000;
    String text = "String s = null" + "+\"xxx\"".repeat(N) + ";";
    final PsiElement expression = ReadAction.computeBlocking(
      () -> JavaPsiFacade.getElementFactory(daemonFixture.get().getFixture().getProject()).createStatementFromText(text, null));
    final int[] n = {0};
    Benchmark.newBenchmark(daemonFixture.get().getFixture().getTestName(false), new ThrowableRunnable() {
      @Override
      public void run() {
        n[0] = 0;
        ReadAction.runBlocking(() -> expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitExpression(@NotNull PsiExpression expression) {
            PsiExpression s = expression;
            super.visitExpression(expression);
            Reference.reachabilityFence(s); //hold on stack
            n[0]++;
          }
        }));
        assertEquals(N+2, n[0]);
      }
    }).start();
  }
  @Test
  public void testHugeMethodChainingVisitingPerformance() throws IncorrectOperationException {
    final int N = 20000;
    String text = "Object s = new StringBuilder()" + ".append(\"xxx\")".repeat(N) + ";";
    final PsiElement expression = ReadAction.computeBlocking(
      () -> JavaPsiFacade.getElementFactory(daemonFixture.get().getFixture().getProject()).createStatementFromText(text, null));
    final int[] n = {0};
    Benchmark.newBenchmark(daemonFixture.get().getFixture().getTestName(false), new ThrowableRunnable() {
      @Override
      public void run() {
        n[0] = 0;
        ReadAction.runBlocking(() -> expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            n[0]++;
          }
        }));
        assertEquals(N, n[0]);
      }
    }).start();
  }
}
