// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class ConstantOnWrongSideOfComparisonInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testConstantOnLHS() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testConstantOnRHS() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    ConstantOnWrongSideOfComparisonInspection inspection = new ConstantOnWrongSideOfComparisonInspection();
    final boolean constantShouldGoLeft = StringUtil.containsIgnoreCase(testName, "rhs");
    inspection.myConstantShouldGoLeft = constantShouldGoLeft;
    inspection.myIgnoreNull = constantShouldGoLeft;
    return inspection;
  }
}