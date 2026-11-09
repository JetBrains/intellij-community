// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class CompareToEqualityInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new CompareToEqualityInspection());

  @Test
  public void testCompareToEquality() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testCompareToEqualityFix() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testMayChangeSemantics() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("compare.to.equality.convert.may.change.semantics.quickfix"));
  }

  @Test
  public void testMayChangeSemanticsEquality() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("compare.to.equality.quickfix.may.change.semantics"));
  }
}
