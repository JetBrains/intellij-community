// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.constant;

import com.siyeh.ipp.IPPTestCase;

public class ConstantSubexpressionIntentionTest extends IPPTestCase {

  public void testPlus() {
    doTest("""
             class X {
               void test(int a) { int res = a + 1 /*_Compute constant value of '1 + 2'*/+ 2 + 3; }
             }""",
           """
             class X {
               void test(int a) { int res = a + 3 + 3; }
             }""");
  }

  public void testMinus() {
    doTestIntentionNotAvailable("""
                                  class X {
                                    void test(int a) { int res = a - 1 /*_Compute constant value of '1 - 2'*/- 2 - 3; }
                                  }""");
  }
}
