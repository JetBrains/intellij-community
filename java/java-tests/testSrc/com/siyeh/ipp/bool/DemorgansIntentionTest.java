// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.bool;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ipp.IPPTestCase;

public class DemorgansIntentionTest extends IPPTestCase {
  public void testNeedsParentheses() { doTest(); }
  public void testNeedsMoreParentheses() { doTest(); }
  public void testNotTooManyParentheses() { doTest(); }
  public void testErrorElement() { doTest(); }
  public void testFlattenPolyadic() { doTest(); }
  public void testUnclosedLiteral() { doTest(); }
  public void testUnclosedLiteral2() { doTest(); }
  public void testClosedLiteral() { doTest(); }
  public void testRegression() { doTest(CommonQuickFixBundle.message("fix.replace.x.with.y", "&&", "||")); }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "||", "&&");
  }

  @Override
  protected String getRelativePath() {
    return "bool/demorgans";
  }
}
