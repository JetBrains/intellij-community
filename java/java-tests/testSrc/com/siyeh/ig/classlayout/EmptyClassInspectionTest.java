// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class EmptyClassInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> getInspection());

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