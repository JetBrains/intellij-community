// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.fixes;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.PointlessBooleanExpressionInspection;

public class PointlessBooleanExpressionFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    PointlessBooleanExpressionInspection inspection = new PointlessBooleanExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = false;
    myFixture.enableInspections(inspection);
    myRelativePath = "pointlessboolean";
    myDefaultHint = InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  public void testNegation() { doTest(); }
  public void testNoBodyCase() { doTest(); }
  public void testNoBodySideEffect() { doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  public void testPolyadic() { doTest(); }
  public void testBoxed() { doTest(); }
  public void testBoxedFalseEqualsInstanceof() { doTest(); }
  public void testBoxedFalseEqualsInstanceofComment() { doTest(); }
  public void testBoxedTrue() { doTest(); }
  public void testBoxedTrueEquals() { doTest(); }
  public void testBoxedTrueEqualsCall() { doTest(); }
  public void testBoxedTrueEqualsComment() { doTest(); }
  public void testBoxedTrueEqualsFQN() { doTest(); }
  public void testBoxedTrueEqualsInstanceof() { doTest(); }
  public void testBoxedTrueEqualsNegated1() { doTest(); }
  public void testBoxedTrueEqualsNegated2() { doTest(); }
  public void testBoxedTrueEqualsNegated3() { doTest(); }
  public void testBoxedTrueParenthesized() { doTest(); }
  public void testSideEffects() { doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  public void testSideEffectsField() { doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  public void testCompoundAssignment1() { doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
  public void testCompoundAssignment2() { doTest(); }
  public void testCompoundAssignment3() { doTest(); }
  public void testCompoundAssignmentSideEffect() { doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
  public void testCompoundAssignmentSideEffect2() { doTest(); }
  public void testCompoundAssignmentSideEffect3() { doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
}
