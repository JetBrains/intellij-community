// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodRecommenderInspection;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

/**
 * @author Bas Leijdekkers
 */
@PerformanceUnitTest
public final class ExtractMethodRecommenderInspectionPerformanceTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testLargeMethodPerformance() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 100;
    myFixture.enableInspections(inspection);

    // reuse test data source from com.intellij.java.codeInsight.psi.ControlFlowPerformanceTest.testManyLocalVariables()
    PsiFile file = myFixture.configureByFile("/psi/controlFlowPerf/ManyLocalVariables.java");
    PsiElementVisitor visitor =
      inspection.buildVisitor(new ProblemsHolder(InspectionManager.getInstance(getProject()), file, false), false);
    Benchmark.newBenchmark(getTestName(false), 
                           () -> PsiTreeUtil.processElements(file, element -> {
                             element.accept(visitor);
                             return true;
                           })).start();
  }
}