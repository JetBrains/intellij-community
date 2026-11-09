// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

@TestApplication
public class LocalVariableHidingMemberVariableInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName), getEnvironmentClasses());

  @Test
  public void testLocalVariableHidingMemberVariable() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testLocalVariableHidingMemberVariableReportAllFields() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    final LocalVariableHidingMemberVariableInspection inspection = new LocalVariableHidingMemberVariableInspection();
    if (testName.endsWith("ReportAllFields")) {
      inspection.m_ignoreInvisibleFields = false;
      inspection.m_ignoreStaticMethods = false;
    }
    return inspection;
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package com.siyeh.igtest.visibility2;
public class DifferentPackageClass
{
    int fooBar;
    protected int fooBar2;
}
"""
    };
  }
}