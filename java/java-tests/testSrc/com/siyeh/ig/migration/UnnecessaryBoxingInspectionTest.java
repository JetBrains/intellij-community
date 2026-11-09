// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
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
public class UnnecessaryBoxingInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryBoxingInspection());

  @Test
  public void testUnnecessaryBoxing() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testUnnecessarySuperfluousBoxing() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryBoxingInspection inspection = new UnnecessaryBoxingInspection();
    inspection.onlyReportSuperfluouslyBoxed = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }
}