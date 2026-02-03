// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.intention;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class FlipCommaIntentionTest extends IPPTestCase {

  public void testFlipParameters() {
    doTest("class C {" +
           "  void foo(int a,/*_Flip ','*/ String b) {}" +
           "}",
           "class C {" +
           "  void foo(String b, int a) {}" +
           "}");
  }

  public void testMultipleFieldsSingleDeclaration() {
    doTest("""
             class C {
                 int a,/*_Flip ','*/ b;
             }""",

           """
             class C {
                 int b, a;
             }""");
  }

  public void testMultipleFieldsSingleDeclaration2() {
    doTest("""
             class C {
               int a, b,/*_Flip ','*/ c;
             }""",

           """
             class C {
               int a, c, b;
             }""");
  }

  public void testMultipleFieldsSingleDeclaration3() {
     doTest("""
              class C {
                String one = "one",/*_Flip ','*/ two ="two", three;
              }""",

            """
              class C {
                String two ="two", one = "one", three;
              }""");
  }

  public void testIncomplete() {
    doTest("""
             class C {
               int a,/*_Flip ','*/ b =;
             }""",

           """
             class C {
               int b =, a;
             }""");
  }

  public void testFlippingBrokenEnumConstantDoesNotCrashWithStubTextMismatch() {
    doTest("""
             enum E {
               A(1)/*_Flip ','*/,
               B(C(2),\s
               D(5);
             }
             """,

           """
             enum E {
               B(C(2),\s
               D(5),
               A(1);
             }
             """);
  }

  public void testUnavailableForDangling() {
    doTestIntentionNotAvailable("class C {\n" +
                                "    int a[] = new int[]{1,2,/*_Flip ','*/};" +
                                "}");
  }

  public void testLeftAndRightIdentical() {
    doTestIntentionNotAvailable("class C {" +
                                "  int[] ones = {1/*_Flip ','*/,1,1};" +
                                "}");
  }

  public void testRecordComponent() {
    doTest("record A(String s,/*_Flip ','*/ int i) { }",
           "record A(int i, String s) { }");
  }
}
