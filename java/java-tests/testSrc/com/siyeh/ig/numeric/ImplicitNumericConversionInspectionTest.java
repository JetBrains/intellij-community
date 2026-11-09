// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ImplicitNumericConversionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ImplicitNumericConversionInspection());

  @Test
  public void testImplicitNumericConversion() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreCharConversion() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreCharConversions = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix"));
  }

  @Test
  public void testIgnoreWidening() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreWideningConversions = true;
    inspection.ignoreConstantConversions = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix"));
  }
}