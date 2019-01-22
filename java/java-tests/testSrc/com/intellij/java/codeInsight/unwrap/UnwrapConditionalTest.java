// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapConditionalTest extends UnwrapTestCase {
  public void testThat() {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n",
                    "xxx('1');\n");
  }

  public void testElse() {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n",
                    "xxx('2' +<caret> 3);\n");
  }

  public void testFromParameterList2() {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n",
                    "xxx(11, '2' +<caret> 3, 12);\n");
  }

  public void testCond1() {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  public void testCond2() {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  public void testUnwrapUnderAssignmentExpression() {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n",
                    "String s = \"1\";\n");
  }
}