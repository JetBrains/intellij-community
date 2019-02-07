// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

/**
 * @author Bas Leijdekkers
 */
public class UnwrapSwitchStatementTest extends UnwrapTestCase {

  public void testSimple() {
    assertUnwrapped("boolean b;\n" +
                    "switch (0) {\n" +
                    "    case 1:\n" +
                    "        b = <caret>false;\n" +
                    "        break;\n" +
                    "    default:\n" +
                    "        b = true;\n" +
                    "        break;\n" +
                    "}",

                    "boolean b;\n" +
                    "b = false;");
  }

  public void testNoBreak() {
    assertUnwrapped("boolean b;\n" +
                    "switch (0) {\n" +
                    "    case 1:\n" +
                    "        b = false;\n" +
                    "        break;\n" +
                    "    default:\n" +
                    "        b = <caret>true;\n" +
                    "}",

                    "boolean b;\n" +
                    "b = true;");
  }

  public void testLabeledBreak() {
    assertUnwrapped("outer: for (int z = 0; z < 10; z++) {\n" +
                    "    boolean b;\n" +
                    "    switch (0) {\n" +
                    "        case 1:\n" +
                    "<caret>            b = false;\n" +
                    "            break outer;\n" +
                    "        default:\n" +
                    "            b = true;\n" +
                    "            break;\n" +
                    "    }\n" +
                    "}",

                    "outer: for (int z = 0; z < 10; z++) {\n" +
                    "    boolean b;\n" +
                    "    b = false;\n" +
                    "    break outer;\n" +
                    "}\n");
  }

  public void testFallThrough() {
    assertUnwrapped("switch (0) {\n" +
                    "    <caret>case 1:\n" +
                    "        System.out.println(1);\n" +
                    "    default:\n" +
                    "        System.out.println(2);\n" +
                    "        break;\n" +
                    "}",

                    "System.out.println(1);\n" +
                    "System.out.println(2);");
  }

  public void testEmptySwitch() {
    assertUnwrapped("switch (1) {\n" +
                    "    case 1:<caret>\n" +
                    "}",

                    "");
  }

  public  void testThrowStatement() {
    assertUnwrapped("switch (0) {\n" +
                    "    case 1: {\n" +
                    "        System.out.println();\n" +
                    "        break;\n" +
                    "    }\n" +
                    "    case 2: <caret>throw null;\n" +
                    "    default:\n" +
                    "        System.out.println();\n" +
                    "        return;\n" +
                    "}",

                    "throw null;");
  }

  public void testRuleBasedBlock() {
    assertUnwrapped("boolean b;\n" +
                    "switch (0) {\n" +
                    "    case 1 -> {\n" +
                    "        <caret>System.out.println();\n" +
                    "        b = false;\n" +
                    "    }\n" +
                    "    case 2 -> throw null;\n" +
                    "    default -> b = true;\n" +
                    "}",

                    "boolean b;\n" +
                    "System.out.println();\n" +
                    "b = false;");
  }

  public void testRuleBasedExpression() {
    assertUnwrapped("boolean b;\n" +
                    "switch (0) {\n" +
                    "    case 1 -> {\n" +
                    "        System.out.println();\n" +
                    "        b = false;\n" +
                    "    }\n" +
                    "    case 2 -> throw null;\n" +
                    "    default<caret> -> b = true;\n" +
                    "}",

                    "boolean b;\n" +
                    "b = true;");
  }

  public void testRuleBasedThrowStatement() {
    assertUnwrapped("boolean b;\n" +
                    "switch (0) {\n" +
                    "    case 1 -> {\n" +
                    "        System.out.println();\n" +
                    "        b = false;\n" +
                    "    }\n" +
                    "    case 2 -> <caret>throw null;\n" +
                    "    default -> b = true;\n" +
                    "}",

                    "boolean b;\n" +
                    "throw null;");
  }

  public void testCommentBracesBreak() {
    assertUnwrapped("boolean b;\n" +
                    "switch (0) {\n" +
                    "    case 1: {\n" +
                    "        System.out.println();\n" +
                    "        b = false;<caret>\n" +
                    "        if (b) break;\n" +
                    "    }\n" +
                    "    default:\n" +
                    "        b = true;\n" +
                    "        //asdf\n" +
                    "        b = true;\n" +
                    "        break;\n" +
                    "}",

                    "boolean b;\n" +
                    "System.out.println();\n" +
                    "b = false;\n" +
                    "if (b) {\n" +
                    "}\n" +
                    "b = true;//asdf\n" +
                    "b = true;",
                    1);
  }
}
