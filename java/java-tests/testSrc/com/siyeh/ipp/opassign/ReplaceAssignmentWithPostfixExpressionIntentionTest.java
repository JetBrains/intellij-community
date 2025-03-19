// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceAssignmentWithPostfixExpressionIntention
 */
public class ReplaceAssignmentWithPostfixExpressionIntentionTest extends IPPTestCase {
  public void testSimple() { doTest(); }
  public void testParentheses() { doTest(); }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "=", "++");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/assignment_to_postfix";
  }
}
