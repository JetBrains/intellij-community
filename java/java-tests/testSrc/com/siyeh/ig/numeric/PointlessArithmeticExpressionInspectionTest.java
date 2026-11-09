/**/// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class PointlessArithmeticExpressionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  private PointlessArithmeticExpressionInspection myInspectionProfileEntry;

  @Test
  public void testPointlessArithmeticExpression() { inspectionFixture.get().doTest(); }
  @Test
  public void testPointlessArithmeticExpression_disable_m_ignoreExpressionsContainingConstants() {
    myInspectionProfileEntry.m_ignoreExpressionsContainingConstants = false;
    inspectionFixture.get().doTest();
  }
  @Test
  public void testNestedVersusSuper() { inspectionFixture.get().doTest(); }
  @Test
  public void testComments() { doQuickFixTest(); }
  @Test
  public void testCast() { doQuickFixTest(); }
  @Test
  public void testCommentInsideCall() { doQuickFixTest(); }
  @Test
  public void testNamedConstants() { inspectionFixture.get().doTest(); }
  @Test
  public void testNamedConstants_disable_m_ignoreExpressionsContainingConstants() {
    myInspectionProfileEntry.m_ignoreExpressionsContainingConstants = false;
    inspectionFixture.get().doTest();
  }

  private void doQuickFixTest() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"));
  }

  @Nullable
  private InspectionProfileEntry getInspection() {
    myInspectionProfileEntry = new PointlessArithmeticExpressionInspection();
    return myInspectionProfileEntry;
  }
}
