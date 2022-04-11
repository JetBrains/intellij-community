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
    doTest("class C {\n" +
           "    int a,/*_Flip ','*/ b;\n" +
           "}",

           "class C {\n" +
           "    int b, a;\n" +
           "}");
  }

  public void testMultipleFieldsSingleDeclaration2() {
    doTest("class C {\n" +
           "  int a, b,/*_Flip ','*/ c;\n" +
           "}",

           "class C {\n" +
           "  int a, c, b;\n" +
           "}");
  }

  public void testMultipleFieldsSingleDeclaration3() {
     doTest("class C {\n" +
            "  String one = \"one\",/*_Flip ','*/ two =\"two\", three;\n" +
            "}",

            "class C {\n" +
            "  String two =\"two\", one = \"one\", three;\n" +
            "}");
  }

  public void testIncomplete() {
    doTest("class C {\n" +
           "  int a,/*_Flip ','*/ b =;\n" +
           "}",

           "class C {\n" +
           "  int b =, a;\n" +
           "}");
  }

  public void testFlippingBrokenEnumConstantDoesNotCrashWithStubTextMismatch() {
    doTest("enum E {\n" +
           "  A(1)/*_Flip ','*/,\n" +
           "  B(C(2), \n" +
           "  D(5);\n" +
           "}\n",

           "enum E {\n" +
           "  B(C(2), \n" +
           "  D(5),\n" +
           "  A(1);\n" +
           "}\n");
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
