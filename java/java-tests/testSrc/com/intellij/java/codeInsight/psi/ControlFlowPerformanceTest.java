// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.psi;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.LocalsControlFlowPolicy;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import one.util.streamex.StreamEx;

public class ControlFlowPerformanceTest extends LightCodeInsightTestCase {
  public void testHugeMethodChainControlFlow() {
    PlatformTestUtil.startPerformanceTest(getTestName(false), 15_000, () -> {
      int size = 2500;
      String source = StreamEx.constant(".toString()", size).joining("", "\"\"", "");
      PsiExpression expression = JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(source, null);
      ControlFlow flow = ControlFlowFactory.getInstance(getProject()).getControlFlow(expression, new LocalsControlFlowPolicy(expression), false);
      assertEquals(size, flow.getSize());
    }).attempts(2).assertTiming();
  }
}
