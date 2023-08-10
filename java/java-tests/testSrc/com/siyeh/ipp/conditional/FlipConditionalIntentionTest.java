// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.conditional;

import com.siyeh.ipp.IPPTestCase;

public class FlipConditionalIntentionTest extends IPPTestCase {

  public void testComments() {
    doTest("class X {" +
           "    void test(boolean foo, boolean bar) {" +
           "        boolean c = false;" +
           "        boolean b = /*_Flip '?:'*/foo ? true : c || bar;//comment at the end\n" +
           "    }" +
           "}",
           "class X {    void test(boolean foo, boolean bar) {        boolean c = false;        boolean b = !foo ? c || bar : true;//comment at the end\n" +
           "    }}");
  }

  public void testIncomplete() {
    doTest("""
             class X {
               void test() {
                 System.out.println(/*_Flip '?:'*/!()?"foo":"bar");
               }
             }""",
           """
             class X {
               void test() {
                 System.out.println(() ? "bar" : "foo");
               }
             }""");
  }

  public void testIncomplete2() {
    doTestIntentionNotAvailable("""
                                  class X {
                                    void test(int a, int b) {
                                      int x = /*_Flip '?:'*/a ? 1 : b ? 2 : ;
                                    }
                                  }""");
  }
}
