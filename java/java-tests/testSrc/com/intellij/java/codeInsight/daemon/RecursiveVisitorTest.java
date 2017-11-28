/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;

/**
 * @author cdr
 */
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
      JavaPsiFacade.getInstance(getProject()).getElementFactory().createStatementFromText(text.toString(), null);
    final int[] n = {0};
    PlatformTestUtil.startPerformanceTest(getTestName(false), 100, new ThrowableRunnable() {
      @Override
      public void run() {
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
      JavaPsiFacade.getInstance(getProject()).getElementFactory().createStatementFromText(text.toString(), null);
    final int[] n = {0};
    PlatformTestUtil.startPerformanceTest(getTestName(false), 200, new ThrowableRunnable() {
      @Override
      public void run() {
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
    }).useLegacyScaling().assertTiming();
  }
}
