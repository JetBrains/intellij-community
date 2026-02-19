// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.threading;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.threading.DoubleCheckedLockingInspection;

/**
 * @author Bas Leijdekkers
 */
public class MakeFieldVolatileFixTest extends IGQuickFixesTestCase {

  public void testSimple() { doTest(InspectionGadgetsBundle.message("double.checked.locking.quickfix", "s_instance")); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DoubleCheckedLockingInspection());
    myRelativePath = "threading/make_field_volatile";
  }
}
