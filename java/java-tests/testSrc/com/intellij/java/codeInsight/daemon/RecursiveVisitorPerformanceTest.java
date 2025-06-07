// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.*;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;

@SkipSlowTestLocally
public class RecursiveVisitorPerformanceTest extends LightDaemonAnalyzerTestCase {
  public void testHugeConcatenationVisitingPerformance() throws IncorrectOperationException {
    final int N = 20000;
    String text = "String s = null" + "+\"xxx\"".repeat(N) + ";";
    final PsiElement expression =
      JavaPsiFacade.getElementFactory(getProject()).createStatementFromText(text, null);
    final int[] n = {0};
    Benchmark.newBenchmark(getTestName(false), new ThrowableRunnable() {
      @Override
      public void run() {
        n[0] = 0;
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitExpression(@NotNull PsiExpression expression) {
            PsiExpression s = expression;
            super.visitExpression(expression);
            Reference.reachabilityFence(s); //hold on stack
            n[0]++;
          }
        });
        assertEquals(N+2, n[0]);
      }
    }).start();
  }
  public void testHugeMethodChainingVisitingPerformance() throws IncorrectOperationException {
    final int N = 20000;
    String text = "Object s = new StringBuilder()" + ".append(\"xxx\")".repeat(N) + ";";
    final PsiElement expression =
      JavaPsiFacade.getElementFactory(getProject()).createStatementFromText(text, null);
    final int[] n = {0};
    Benchmark.newBenchmark(getTestName(false), new ThrowableRunnable() {
      @Override
      public void run() {
        n[0] = 0;
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            n[0]++;
          }
        });
        assertEquals(N, n[0]);
      }
    }).start();
  }
}
