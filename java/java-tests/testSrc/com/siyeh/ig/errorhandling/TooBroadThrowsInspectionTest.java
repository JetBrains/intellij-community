// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;

public class TooBroadThrowsInspectionTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    TooBroadThrowsInspection inspection = new TooBroadThrowsInspection();
    inspection.hiddenExceptionsThreshold = 5;
    myFixture.enableInspections(inspection);
    myRelativePath = "toobroadthrows";
    myDefaultHint = InspectionGadgetsBundle.message("overly.broad.throws.clause.quickfix2");
  }

  public void testNotFoundInsteadOfIOException() { doTest(); }
  public void testThrowNull() { doTest(); }
  public void testGenericThrow() { doTest(); }
  public void testMultiCatch() { doTest(InspectionGadgetsBundle.message("overly.broad.throws.clause.quickfix1")); }

  public void testMoreExceptionsThanThreshold() { doTest(); }
}