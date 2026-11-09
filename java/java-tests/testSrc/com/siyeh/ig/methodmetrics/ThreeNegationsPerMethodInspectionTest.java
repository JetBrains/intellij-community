package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ThreeNegationsPerMethodInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testThreeNegationsPerMethod() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final ThreeNegationsPerMethodInspection inspection = new ThreeNegationsPerMethodInspection();
    inspection.m_ignoreInEquals = true;
    inspection.ignoreInAssert = true;
    return inspection;
  }
}