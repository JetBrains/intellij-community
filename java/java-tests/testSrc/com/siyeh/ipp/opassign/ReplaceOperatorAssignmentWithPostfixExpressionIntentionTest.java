// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceOperatorAssignmentWithPostfixExpressionIntentionTest extends IPPTestCase {

  public void testSimple() {
    doTest("""
             class X {{
                 int i = 10;
                 i /*_*/+= (1);
             }}""",

           """
             class X {{
                 int i = 10;
                 i++;
             }}""");
  }

  public void testBoxed() {
    doTest("""
             class X {{
                 Integer i = 10;
                 i /*_*/+= 1;
             }}""",

           """
             class X {{
                 Integer i = 10;
                 i++;
             }}""");
  }

  public void testString() {
    doTestIntentionNotAvailable("class X {{" +
                                "  String s = \"zz\";" +
                                "  s /*_*/+= 1;" +
                                "}}");
  }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "+=", "++");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/assignment";
  }
}