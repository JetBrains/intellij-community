// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class EmptyClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testEmptyClass() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testPackageInfo() {
    inspectionFixture.get().doNamedTest("package-info");
  }

  @Test
  public void testEmptyFile() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testClassWithComments() {
    inspectionFixture.get().doTest();
  }

  private static InspectionProfileEntry getInspection() {
    final EmptyClassInspection inspection = new EmptyClassInspection();
    inspection.ignoreClassWithParameterization = true;
    inspection.ignoreThrowables = true;
    inspection.commentsAreContent = true;
    return inspection;
  }
}