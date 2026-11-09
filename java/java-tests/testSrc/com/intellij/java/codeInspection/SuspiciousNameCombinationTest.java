// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class SuspiciousNameCombinationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/suspiciousNameCombination");
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    SuspiciousNameCombinationInspection inspection = new SuspiciousNameCombinationInspection();
    inspection.addNameGroup("someWord,otherWord");
    return inspection;
  }

  @Test
  public void testAssignment() { inspectionFixture.get().doTest();}
  @Test
  public void testInitializer() { inspectionFixture.get().doTest();}
  @Test
  public void testParameter() { inspectionFixture.get().doTest();}
  @Test
  public void testReturnValue() { inspectionFixture.get().doTest();}
  @Test
  public void testExcluded() { inspectionFixture.get().doTest();}
  @Test
  public void testTwoWords() { inspectionFixture.get().doTest();}
}
