package com.intellij.codeInsight.daemon;

import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;

/**
 * @author cdr
 */
public class RecursiveVisitorTest extends LightDaemonAnalyzerTestCase{
  public void testHugeConcatenationVisitingPerformance() throws IncorrectOperationException {
    StringBuilder text = new StringBuilder("String s = null");
    final int N = 20000;
    for (int i = 0; i < N; i++) {
      text.append("+\"xxx\"");
    }
    text.append(";");
    final PsiElement expression =
      JavaPsiFacade.getInstance(getProject()).getElementFactory().createStatementFromText(text.toString(), null);
    final int[] n = {0};
    PlatformTestUtil.startPerformanceTest(getTestName(false), 100, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        n[0] = 0;
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitExpression(PsiExpression expression) {
            PsiExpression s = expression;
            super.visitExpression(expression);
            s.hashCode(); //hold on stack
            n[0]++;
          }
        });
        assertEquals(N+2, n[0]);
      }
    }).cpuBound().assertTiming();
  }
  public void testHugeMethodChainingVisitingPerformance() throws IncorrectOperationException {
    StringBuilder text = new StringBuilder("Object s = new StringBuilder()");
    final int N = 20000;
    for (int i = 0; i < N; i++) {
      text.append(".append(\"xxx\")");
    }
    text.append(";");
    final PsiElement expression =
      JavaPsiFacade.getInstance(getProject()).getElementFactory().createStatementFromText(text.toString(), null);
    final int[] n = {0};
    PlatformTestUtil.startPerformanceTest(getTestName(false), 200, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        n[0] = 0;
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            n[0]++;
          }
        });
        assertEquals(N, n[0]);
      }
    }).cpuBound().assertTiming();
  }
}
