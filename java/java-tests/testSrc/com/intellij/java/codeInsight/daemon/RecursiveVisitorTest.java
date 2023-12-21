// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

@SkipSlowTestLocally
public class RecursiveVisitorTest extends LightDaemonAnalyzerTestCase {
  public void testHugeConcatenationVisitingPerformance() throws IncorrectOperationException {
    StringBuilder text = new StringBuilder("String s = null");
    final int N = 20000;
    for (int i = 0; i < N; i++) {
      text.append("+\"xxx\"");
    }
    text.append(";");
    final PsiElement expression =
      JavaPsiFacade.getElementFactory(getProject()).createStatementFromText(text.toString(), null);
    final int[] n = {0};
    PlatformTestUtil.startPerformanceTest(getTestName(false), 100, new ThrowableRunnable() {
      @Override
      public void run() {
        n[0] = 0;
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitExpression(@NotNull PsiExpression expression) {
            PsiExpression s = expression;
            super.visitExpression(expression);
            s.hashCode(); //hold on stack
            n[0]++;
          }
        });
        assertEquals(N+2, n[0]);
      }
    }).useLegacyScaling().assertTiming();
  }
  public void testHugeMethodChainingVisitingPerformance() throws IncorrectOperationException {
    StringBuilder text = new StringBuilder("Object s = new StringBuilder()");
    final int N = 20000;
    for (int i = 0; i < N; i++) {
      text.append(".append(\"xxx\")");
    }
    text.append(";");
    final PsiElement expression =
      JavaPsiFacade.getElementFactory(getProject()).createStatementFromText(text.toString(), null);
    final int[] n = {0};
    PlatformTestUtil.startPerformanceTest(getTestName(false), 200, new ThrowableRunnable() {
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
    }).useLegacyScaling().assertTiming();
  }

  public void testStopWalking() {
    PsiJavaFile file = (PsiJavaFile)createFile("Test.java", """
      class Test {
        Test() {
          super();
          super();
          {
            super();
          }
        }
      }""");
    int[] count = {0};
    PsiCodeBlock body = file.getClasses()[0].getMethods()[0].getBody();
    body.acceptChildren(new JavaRecursiveElementWalkingVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        count[0]++;
        stopWalking();
      }
    });
    assertEquals(1, count[0]);
  }
}
