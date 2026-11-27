// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.fixes;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.PointlessBooleanExpressionInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class PointlessBooleanExpressionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    PointlessBooleanExpressionInspection inspection = new PointlessBooleanExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = false;
    return List.of(inspection);
  }, "pointlessboolean", InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Test
  public void testNegation() { ig.get().doTest(); }
  @Test
  public void testNoBodyCase() { ig.get().doTest(); }
  @Test
  public void testNoBodySideEffect() { ig.get().doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  @Test
  public void testPolyadic() { ig.get().doTest(); }
  @Test
  public void testPolyadic2() { ig.get().doTest(); }
  @Test
  public void testBoxed() { ig.get().doTest(); }
  @Test
  public void testBoxedFalseEqualsInstanceof() { ig.get().doTest(); }
  @Test
  public void testBoxedFalseEqualsInstanceofComment() { ig.get().doTest(); }
  @Test
  public void testBoxedTrue() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEquals() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsCall() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsComment() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsFQN() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsInstanceof() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsNegated1() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsNegated2() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueEqualsNegated3() { ig.get().doTest(); }
  @Test
  public void testBoxedTrueParenthesized() { ig.get().doTest(); }
  @Test
  public void testSideEffects() { ig.get().doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  @Test
  public void testSideEffectsField() { ig.get().doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  @Test
  public void testCompoundAssignment1() { ig.get().doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
  @Test
  public void testCompoundAssignment2() { ig.get().doTest(); }
  @Test
  public void testCompoundAssignment3() { ig.get().doTest(); }
  @Test
  public void testCompoundAssignmentSideEffect() { ig.get().doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
  @Test
  public void testCompoundAssignmentSideEffect2() { ig.get().doTest(); }
  @Test
  public void testCompoundAssignmentSideEffect3() { ig.get().doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
}
