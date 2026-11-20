// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class ConditionCoveredByFurtherConditionInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new ConditionCoveredByFurtherConditionInspection());

  // The test expects a duplicate warning, and only the IDE reports it twice.
  @AnalysisHostTest(hosts = AnalysisHostTest.Host.IDE)
  @Test
  public void testConditionCoveredByFurtherCondition() {
    ExpectedHighlightingData.expectedDuplicatedHighlighting(()->inspectionFixture.get().doTest());
  }
  @Test
  public void testMultiCatch() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testExpressionSwitch() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testStringLength() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testInInjection() {
    inspectionFixture.get().doTest();
  }
}
