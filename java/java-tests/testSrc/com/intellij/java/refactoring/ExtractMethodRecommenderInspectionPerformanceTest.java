// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethod.ExtractMethodRecommenderInspection;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

import java.util.Collections;

/**
 * @author Bas Leijdekkers
 */
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
    AnalysisScope scope = new AnalysisScope(file);
    LocalInspectionToolWrapper toolWrapper = new LocalInspectionToolWrapper(inspection);
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    Benchmark.newBenchmark(getTestName(false), () -> InspectionTestUtil.runTool(toolWrapper, scope, globalContext)).start();
  }
}