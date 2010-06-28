package com.intellij.codeInsight.daemon;

import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author cdr
 */
public class RecursiveVisitorTest extends LightDaemonAnalyzerTestCase{
  public void testHugeConcatenationVisitingPerformance() throws IncorrectOperationException {
    StringBuilder text = new StringBuilder("String s = \"xxx\"");
    final int N = 2;
    for (int i=0;i< N;i++) {
      text.append("+\"xxx\"");
    }
    text.append(";");
    final PsiElement expression = JavaPsiFacade.getInstance(getProject()).getElementFactory().createStatementFromText(text.toString(), null);
    final int[] n = {0};
    IdeaTestUtil.assertTiming("",20,new Runnable(){
      public void run() {
        n[0]=0;
        expression.accept(new JavaRecursiveElementWalkingVisitor(){
          public void visitBinaryExpression(final PsiBinaryExpression expression) {
            PsiExpression s = expression.getLOperand();
            super.visitBinaryExpression(expression);
            s.hashCode(); //hold on stack
            n[0]++;
          }
        });
        assertEquals(N, n[0]);
      }
    });
  }
  public void testHugeMethodChainingVisitingPerformance() throws IncorrectOperationException {
    StringBuilder text = new StringBuilder("Object s = new StringBuilder()");
    final int N = 1500;
    for (int i=0;i< N;i++) {
      text.append(".append(\"xxx\")");
    }
    text.append(";");
    final PsiElement expression = JavaPsiFacade.getInstance(getProject()).getElementFactory().createStatementFromText(text.toString(), null);
    final int[] n = {0};
    IdeaTestUtil.assertTiming("",100,new Runnable(){
      public void run() {
        n[0]=0;
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            n[0]++;
          }
        });
        assertEquals(N, n[0]);
      }
    });
  }
}
