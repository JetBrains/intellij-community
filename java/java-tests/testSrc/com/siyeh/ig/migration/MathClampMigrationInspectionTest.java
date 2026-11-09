// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

@TestApplication
public final class MathClampMigrationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testMathClampMigration() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testInvalidInputs() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testFullyQualifiedName() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testMathClampBraveMode() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
  
  private static InspectionProfileEntry getInspection(String testName) {
    MathClampMigrationInspection inspection = new MathClampMigrationInspection();
    if (!testName.contains("BraveMode")) {
      inspection.braveMode = false;
    }
    return inspection;
  }
}
