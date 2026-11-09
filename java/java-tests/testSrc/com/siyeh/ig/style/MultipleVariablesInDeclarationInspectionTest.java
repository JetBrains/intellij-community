// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class MultipleVariablesInDeclarationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testOnlyWarnArrayDimensions() { inspectionFixture.get().doTest(); }
  @Test
  public void testMultipleVariableDeclaration() { inspectionFixture.get().doTest(); }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    MultipleVariablesInDeclarationInspection inspection = new MultipleVariablesInDeclarationInspection();
    if ("OnlyWarnArrayDimensions".equals(testName)) {
      inspection.onlyWarnArrayDimensions = true;
    }
    else {
      inspection.ignoreForLoopDeclarations = false;
    }
    return inspection;
  }
}