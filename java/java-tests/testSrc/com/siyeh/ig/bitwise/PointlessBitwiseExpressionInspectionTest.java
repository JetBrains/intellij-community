// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bitwise;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

@TestApplication
public class PointlessBitwiseExpressionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture = lightJavaInspectionFixtureByTestName(projectFixture, testName -> {
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
