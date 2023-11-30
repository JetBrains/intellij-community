// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.security;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.security.CloneableClassInSecureContextInspection;

/**
 * @author Bas Leijdekkers
 */
public class RemoveCloneableFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CloneableClassInSecureContextInspection());
    myRelativePath = "security/remove_cloneable";
    myDefaultHint = InspectionGadgetsBundle.message("remove.cloneable.quickfix");
  }

  public void testCloneUsed() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.cloneable.quickfix"));
  }

  public void testSimple() { doTest(); }
  public void testSafeCloneCall() { doTest(); }
}
