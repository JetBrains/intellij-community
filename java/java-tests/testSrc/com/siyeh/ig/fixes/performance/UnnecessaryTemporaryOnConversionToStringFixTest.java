// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.UnnecessaryTemporaryOnConversionToStringInspection;

public class UnnecessaryTemporaryOnConversionToStringFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryTemporaryOnConversionToStringInspection());
    myRelativePath = "performance/unnecessary_temporary_to_string";
  }

  public void testIntegerToString() { doTest("Replace with 'Integer.toString(i[/*zero*/0])'"); }
}