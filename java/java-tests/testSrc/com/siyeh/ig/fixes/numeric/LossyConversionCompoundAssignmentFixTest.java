// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.LossyConversionCompoundAssignmentInspection;

public class LossyConversionCompoundAssignmentFixTest extends IGQuickFixesTestCase {

  public void testLongTimesDouble() {
    doTest(InspectionGadgetsBundle.message(
      "inspection.lossy.conversion.compound.assignment.expand.fix.name", "long"));
  }

  public void testLongMultiplyDouble() {
    doTest(QuickFixBundle.message("add.typecast.cast.text", "long",
                                  QuickFixBundle.message("fix.expression.role.expression")));
  }

  public void testBytePlusShortNotExpected() {
    assertQuickfixNotAvailable(QuickFixBundle.message("add.typecast.cast.text", "byte",
                                  QuickFixBundle.message("fix.expression.role.expression")));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new LossyConversionCompoundAssignmentInspection());
    myRelativePath = "numeric/lossy_conversion_compound_assignment";
  }
}
