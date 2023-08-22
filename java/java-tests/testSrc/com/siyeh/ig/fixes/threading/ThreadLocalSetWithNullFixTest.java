// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.threading;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.threading.ThreadLocalSetWithNullInspection;

public class ThreadLocalSetWithNullFixTest extends IGQuickFixesTestCase {
  public void testSimple() { doTest(InspectionGadgetsBundle.message("thread.local.set.with.null.quickfix")); }

  public void testComments() { doTest(InspectionGadgetsBundle.message("thread.local.set.with.null.quickfix")); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ThreadLocalSetWithNullInspection());
    myRelativePath = "threading/thread_local_set_with_null";
  }
}
