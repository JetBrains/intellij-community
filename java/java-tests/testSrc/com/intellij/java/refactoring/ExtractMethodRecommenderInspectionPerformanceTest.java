// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodRecommenderInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Bas Leijdekkers
 */
@PerformanceUnitTest
@StressTestApplication
public final class ExtractMethodRecommenderInspectionPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  @Test
  public void testLargeMethodPerformance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 100;
    myFixture.enableInspections(inspection);

    // reuse test data source from com.intellij.java.codeInsight.psi.ControlFlowPerformanceTest.testManyLocalVariables()
    PsiFile file = myFixture.configureByFile("/psi/controlFlowPerf/ManyLocalVariables.java");
    PsiElementVisitor visitor =
      inspection.buildVisitor(new ProblemsHolder(InspectionManager.getInstance(myFixture.getProject()), file, false), false);
    Benchmark.newBenchmark(myFixture.getTestName(false), 
                           () -> EdtTestUtil.runInEdtAndWait(() -> PsiTreeUtil.processElements(file, element -> {
                             element.accept(visitor);
                             return true;
                           }))).start();
  }
}