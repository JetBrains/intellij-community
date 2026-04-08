// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplacePostfixExpressionWithAssignmentIntention
 */
public class ReplacePostfixExpressionWithAssignmentIntentionTest extends IPPTestCase {

  public void testIntIncrement() {
    doTest("""
             class X {{
                 int i = 10;
                 i/*_*/++;
             }}""",

           """
             class X {{
                 int i = 10;
                 i = i + 1;
             }}""");
  }

  public void testLongIncrement() {
    doTest("""
             class X {{
                 long i = 10;
                 i/*_*/++;
             }}""",

           """
             class X {{
                 long i = 10;
                 i = i + 1;
             }}""");
  }

  public void testByteIncrement() {
    doTestIntentionNotAvailable("""
                                  class X {{
                                      byte i = 10;
                                      i/*_*/++;
                                  }}""");
  }

  public void testCharIncrement() {
    doTestIntentionNotAvailable("""
                                  class X {{
                                      char i = 10;
                                      i/*_*/++;
                                  }}""");
  }

  public void testShortIncrement() {
    doTestIntentionNotAvailable("""
                                  class X {{
                                      short i = 10;
                                      i/*_*/++;
                                  }}""");
  }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "++", "=");
  }
}
