// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.ImplicitNumericConversionInspection;

/**
 * @author Bas Leijdekkers
 */
public class ImplicitNumericConversionFixTest extends IGQuickFixesTestCase {

  public void testOperatorAssignment() { doTest(); }
  public void testOperatorAssignment2() { doTest(); }
  public void testOperatorAssignment3() { doTest(); }
  public void testOperatorAssignment4() { doTest(); }
  public void testPlainAssignment() { doTest(); }
  public void testHexadecimalLiteral() { doTest(); }
  public void testParentheses() { doTest("Convert to '100L'"); }
  public void testArrayAccess() { doTest(); }
  public void testPrecedence() { doTest(); }
  public void testCharConversion() { doTest(); }
  public void testCharConversion2() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreCharConversions = true;
    myFixture.enableInspections(inspection);
    myDefaultHint = InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix");
  }

  @Override
  protected String getRelativePath() {
    return "numeric/implicit_numeric_conversion";
  }
}
