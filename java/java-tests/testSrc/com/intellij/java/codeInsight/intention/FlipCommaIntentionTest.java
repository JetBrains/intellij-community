/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
}
