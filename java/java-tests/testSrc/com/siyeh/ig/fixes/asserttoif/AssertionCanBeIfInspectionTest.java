// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.asserttoif;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.asserttoif.AssertionCanBeIfInspection;

/**
 * @author Bas Leijdekkers
 */
public class AssertionCanBeIfInspectionTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new AssertionCanBeIfInspection());
    myRelativePath = "asserttoif/assert_to_if";
    myDefaultHint = InspectionGadgetsBundle.message("assert.can.be.if.quickfix");
  }

  public void testIncomplete() { assertQuickfixNotAvailable(); }
  public void testIncomplete2() { assertQuickfixNotAvailable(); }
  public void testMessage() { doTest(); }
  public void testNeedsCodeBlock() { doTest(); }
}
