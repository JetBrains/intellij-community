// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.StaticFieldReferenceOnSubclassInspection;

public class StaticFieldReferenceOnSubclassTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StaticFieldReferenceOnSubclassInspection());
  }

  public void testPreserveComments() {
    doTest(getTestName(true), InspectionGadgetsBundle.message("static.field.via.subclass.rationalize.quickfix"));
  }

  @Override
  protected String getRelativePath() {
    return "bugs/staticFieldRefOnSubclass";
  }
}
