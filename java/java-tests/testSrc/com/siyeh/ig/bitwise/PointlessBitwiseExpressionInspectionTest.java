// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bitwise;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixtureByTestName;

@AnalysisHostTest
public class PointlessBitwiseExpressionInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture = javaInspectionFixtureByTestName(projectFixture, testName -> {
    PointlessBitwiseExpressionInspection inspection = new PointlessBitwiseExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = testName.endsWith("NoConstants");
    return inspection;
  });

  @Test
  public void testPointlessBitwiseExpression() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testPointlessBitwiseExpressionNoConstants() {
    inspectionFixture.get().doTest();
  }
}
