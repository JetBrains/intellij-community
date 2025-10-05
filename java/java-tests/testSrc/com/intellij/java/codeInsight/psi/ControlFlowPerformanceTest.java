// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.psi;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsControlFlowPolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

public class ControlFlowPerformanceTest extends LightJavaCodeInsightTestCase {
  @NonNls private static final String PATH = "/psi/controlFlowPerf/";

  public void testManyLocalVariables() {
    configureByFile(PATH + getTestName(false) + ".java");
    PsiFile file = getFile();
    Benchmark.newBenchmark(getTestName(false), () -> {
      PsiTreeUtil.processElements(file, element -> {
        if (element instanceof PsiLocalVariable var) {
          final PsiCodeBlock scope = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
          ControlFlowUtil.isEffectivelyFinal(var, scope);
        }
        return true;
      });
    }).start();
  }

  public void testHugeMethodChainControlFlow() {
    Benchmark.newBenchmark(getTestName(false), () -> {
      int size = 2500;
      String source = StreamEx.constant(".toString()", size).joining("", "\"\"", "");
      PsiExpression expression = JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(source, null);
      ControlFlow flow = ControlFlowFactory.getInstance(getProject()).getControlFlow(expression, new LocalsControlFlowPolicy(expression), false);
      assertEquals(size, flow.getSize());
    }).attempts(2).start();
  }
}
