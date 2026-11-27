// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.psi;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsControlFlowPolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@PerformanceUnitTest
@StressTestApplication
public class ControlFlowPerformanceTest {
  @NonNls private static final String PATH = "/psi/controlFlowPerf/";

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testManyLocalVariables() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    String testName = myFixture.getTestName(false);
    PsiFile file = daemonFixture.get().configureByFile(PATH + testName + ".java");
    ReadAction.runBlocking(() -> {
      Benchmark.newBenchmark(testName, () -> {
        PsiTreeUtil.processElements(file, element -> {
          if (element instanceof PsiLocalVariable var) {
            final PsiCodeBlock scope = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
            ControlFlowUtil.isEffectivelyFinal(var, scope);
          }
          return true;
        });
      }).start();
    });
  }

  @Test
  public void testHugeMethodChainControlFlow() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Project project = myFixture.getProject();
    ReadAction.runBlocking(() -> {
      Benchmark.newBenchmark(myFixture.getTestName(false), () -> {
        int size = 2500;
        String source = StreamEx.constant(".toString()", size).joining("", "\"\"", "");
        PsiExpression expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(source, null);
        ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(expression, new LocalsControlFlowPolicy(expression), false);
        assertEquals(size, flow.getSize());
      }).attempts(2).start();
    });
  }
}
