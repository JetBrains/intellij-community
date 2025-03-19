// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.StringEqualsEmptyStringInspection;

public class StringEqualsEmptyStringFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StringEqualsEmptyStringInspection inspection = new StringEqualsEmptyStringInspection();
    inspection.SUPPRESS_FOR_VALUES_WHICH_COULD_BE_NULL = false;
    myFixture.enableInspections(inspection);
    myRelativePath = "performance/replace_with_isempty";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "isEmpty()");
  }

  public void testSimple() { doTest(); }
  public void testNullCheck() { doTest(); }
  public void testNullCheckSuppress() {
    myFixture.enableInspections(new StringEqualsEmptyStringInspection());
    assertQuickfixNotAvailable();
  }
  public void testNullCheckAlreadyPresent() { doTest(); }
  public void testTernary() { doTest(); }
  public void testNullCheckNegation() { doTest(); }
  public void testTernaryNegation() { doTest(); }
}
