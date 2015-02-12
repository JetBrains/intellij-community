package com.intellij.codeInsight.unwrap;

public class UnwrapConditionalTest extends UnwrapTestCase {
  public void testThat() throws Exception {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n",
                    "xxx('1');\n");
  }

  public void testElse() throws Exception {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n",
                    "xxx('2' +<caret> 3);\n");
  }

  public void testFromParameterList2() throws Exception {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n",
                    "xxx(11, '2' +<caret> 3, 12);\n");
  }

  public void testCond1() throws Exception {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  public void testCond2() throws Exception {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  public void testUnwrapUnderAssigmentExpression() throws Exception {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n",
                    "String s = \"1\";\n");
  }
}