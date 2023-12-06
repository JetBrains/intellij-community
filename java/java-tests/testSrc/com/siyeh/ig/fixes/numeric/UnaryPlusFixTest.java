// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.numeric;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.UnaryPlusInspection;

public class UnaryPlusFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    UnaryPlusInspection inspection = new UnaryPlusInspection();
    inspection.onlyReportInsideBinaryExpression = false;
    myFixture.enableInspections(inspection);
    myRelativePath = "numeric/unary_plus";
  }

  public void testUnaryPlus() {
    doTest("Fix all 'Unary plus' problems in file");
  }

  public void testFinalVariable() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }

  public void testLocalVariable() {
    doTest(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }

  public void testMethodCall() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }

  public void testCommentBetweenOperatorAndOperand() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "++", "i"));
  }
}
