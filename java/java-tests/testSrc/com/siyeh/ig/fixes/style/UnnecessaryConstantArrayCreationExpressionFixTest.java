// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessaryConstantArrayCreationExpressionInspection;

public class UnnecessaryConstantArrayCreationExpressionFixTest extends IGQuickFixesTestCase {

  public void testPrimitive() { doTest("int[]"); }
  public void testParenthesized() { doTest("int[]"); }
  public void testTwoDimension() { doTest("Map[][]"); }
  public void testInitializerWithoutNew() { assertQuickfixNotAvailable(); }

  @Override
  protected void doTest(String hint) {
    super.doTest(CommonQuickFixBundle.message("fix.remove", "new " + hint));
  }

  @Override
  protected void assertQuickfixNotAvailable() {
    super.assertQuickfixNotAvailable(CommonQuickFixBundle.message("fix.remove", ""));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryConstantArrayCreationExpressionInspection());
    myRelativePath = "style/unnecessary_constant_array_creation_expression";
  }
}
