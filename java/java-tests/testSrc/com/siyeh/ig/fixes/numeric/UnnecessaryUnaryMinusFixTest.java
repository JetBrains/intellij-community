// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.numeric;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.numeric.UnnecessaryUnaryMinusInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnnecessaryUnaryMinusFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryUnaryMinusInspection()), "numeric/unnecessary_unary_minus");

  @Test
  public void testUnaryMinus() {
    ig.get().doTest("Fix all 'Unnecessary unary minus' problems in file");
  }

  @Test
  public void testFinalVariable() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }

  @Test
  public void testLocalVariableRemoveMinus() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.unary.minus.remove.quickfix", "i"));
  }

  @Test
  public void testLocalVariableDecrement() {
    ig.get().doTest(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }

  @Test
  public void testMethodCall() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }

  @Test
  public void testCommentBetweenOperatorAndOperand() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }
}
