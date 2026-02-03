// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.SuspiciousIntegerDivAssignmentInspection;

public class SuspiciousIntegerDivAssignmentFixTest extends IGQuickFixesTestCase {

  public void testDivAssignmentOperator() {
    doTest();
  }

  public void testMultAssignmentOperator() {
    doTest();
  }

  public void testCharacterDivision() {
    doTest();
  }

  public void testParenthesizedRhs() {
    doTest();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SuspiciousIntegerDivAssignmentInspection());
    myDefaultHint = InspectionGadgetsBundle.message("suspicious.integer.div.assignment.quickfix");
    myRelativePath = "bugs/suspicious_integer_div_assignment";
  }
}
