// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.UnpredictableBigDecimalConstructorCallInspection;

/**
 * @author Bas Leijdekkers
 */
public class UnpredictableBigDecimalConstructorCallFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final UnpredictableBigDecimalConstructorCallInspection inspection = new UnpredictableBigDecimalConstructorCallInspection();
    inspection.ignoreReferences = false;
    myFixture.enableInspections(inspection);
    myFixture.addClass(
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal(double d) {}" +
      "}"
    );
  }

  @Override
  protected String getRelativePath() {
    return "numeric/unpredictable_big_decimal";
  }

  public void testFactory() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "BigDecimal.valueOf(val)"));
  }

  public void testConstructor() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "new BigDecimal(\"0.1\")"));
  }

  public void testLiteral() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "new BigDecimal(\"2\")"));
  }

  public void testUnderscores() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "new BigDecimal(\"1000.1\")"));
  }
}
