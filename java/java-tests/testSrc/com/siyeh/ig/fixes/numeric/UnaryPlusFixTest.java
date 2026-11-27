// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.numeric;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.numeric.UnaryPlusInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnaryPlusFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    UnaryPlusInspection inspection = new UnaryPlusInspection();
    inspection.onlyReportInsideBinaryExpression = false;
    return List.of(inspection);
  }, "numeric/unary_plus");

  @Test
  public void testUnaryPlus() {
    ig.get().doTest("Fix all 'Unary plus' problems in file");
  }

  @Test
  public void testFinalVariable() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }

  @Test
  public void testLocalVariable() {
    ig.get().doTest(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }

  @Test
  public void testMethodCall() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }

  @Test
  public void testCommentBetweenOperatorAndOperand() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }
}
