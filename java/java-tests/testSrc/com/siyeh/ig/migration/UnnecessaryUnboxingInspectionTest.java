package com.siyeh.ig.migration;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UnnecessaryUnboxingInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryUnboxingInspection());

  @Test
  public void testUnnecessaryUnboxing() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testUnnecessarySuperfluousUnboxing() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryUnboxingInspection inspection = new UnnecessaryUnboxingInspection();
    inspection.onlyReportSuperfluouslyUnboxed = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }
}