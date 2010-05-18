/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold java formatting indentation-specific tests.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:29:25 PM
 */
public class JavaFormatterIndentationTest extends AbstractJavaFormatterTest {

  public void testClassInitializationBlockIndentation() throws Exception {
    // Checking that initialization block body is correctly indented.
    doMethodTest(
      "checking(new Expectations() {{\n" +
      "one(tabConfiguration).addFilter(with(equal(PROPERTY)), with(aListContaining(\"a-c\")));\n" +
      "}});",
      "checking(new Expectations() {{\n" +
      "    one(tabConfiguration).addFilter(with(equal(PROPERTY)), with(aListContaining(\"a-c\")));\n" +
      "}});"
    );

    // Checking that closing curly brace of initialization block that is not the first block on a line is correctly indented.
    doTextTest("class Class {\n" + "    private Type field; {\n" + "    }\n" + "}",
               "class Class {\n" + "    private Type field; {\n" + "    }\n" + "}");
    doTextTest(
      "class T {\n" +
      "    private final DecimalFormat fmt = new DecimalFormat(); {\n" +
      "        fmt.setGroupingUsed(false);\n" +
      "        fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));\n" +
      "    }\n" +
      "}",
      "class T {\n" +
      "    private final DecimalFormat fmt = new DecimalFormat(); {\n" +
      "        fmt.setGroupingUsed(false);\n" +
      "        fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));\n" +
      "    }\n" +
      "}"
    );
  }

  public void testNestedMethodsIndentation() throws Exception {
    // Inspired by IDEA-43962

    getSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 4;

    doMethodTest(
      "BigDecimal.ONE\n" +
      "      .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      ".add(BigDecimal.ONE\n" +
      " .add(BigDecimal.ONE\n" +
      "  .add(BigDecimal.ONE\n" +
      " .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE)))))))));",
      "BigDecimal.ONE\n" +
      "    .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      "            .add(BigDecimal.ONE\n" +
      "                .add(BigDecimal.ONE\n" +
      "                    .add(BigDecimal.ONE\n" +
      "                        .add(BigDecimal.ONE\n" +
      "                            .add(BigDecimal.ONE\n" +
      "                                .add(BigDecimal.ONE\n" +
      "                                    .add(BigDecimal.ONE)))))))));"
    );
  }

  public void testShiftedChainedIfElse() throws Exception {
    getSettings().BRACE_STYLE = CodeStyleSettings.NEXT_LINE_SHIFTED2;
    getSettings().ELSE_ON_NEW_LINE = true;
    getSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 4;
    doMethodTest(
      "long a = System.currentTimeMillis();\n" +
      "    if (a == 0){\n" +
      "   }else if (a > 1){\n" +
      "  }else if (a > 2){\n" +
      " }else if (a > 3){\n" +
      "     }else if (a > 4){\n" +
      "      }else if (a > 5){\n" +
      "       }else{\n" +
      "        }",
      "long a = System.currentTimeMillis();\n" +
      "if (a == 0)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 1)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 2)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 3)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 4)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 5)\n" +
      "    {\n" +
      "    }\n" +
      "else\n" +
      "    {\n" +
      "    }"
    );
  }

  public void testAlignedSubBlockIndentation() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;

    // Inspired by IDEA-54671
    doTextTest(
      "class Test {\n" +
      "    public void foo() {\n" +
      "        test(11\n" +
      "                     + 12\n" +
      "                     + 13,\n" +
      "             21\n" +
      "                     + 22\n" +
      "                     + 23\n" +
      "        )" +
      "    }\n" +
      "}",

      "class Test {\n" +
      "    public void foo() {\n" +
      "        test(11\n" +
      "                     + 12\n" +
      "                     + 13,\n" +
      "             21\n" +
      "                     + 22\n" +
      "                     + 23\n" +
      "        )\n" +
      "    }\n" +
      "}"
    );
  }

  public void testEnumIndentation() throws IncorrectOperationException {
    // Inspired by IDEADEV-2840
    doTextTest("enum Xyz {\n" + "FOO,\n" + "BAR,\n" + "}", "enum Xyz {\n" + "    FOO,\n" + "    BAR,\n" + "}");
  }

  public void testFirstColumnComment() throws IncorrectOperationException {
    // Inspired by IDEADEV-14116
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    doTextTest("class Foo{\n" + "private int foo;     // this is a foo\n" + "}",
               "class Foo {\n" + "    private int foo;     // this is a foo\n" + "}");
  }

  public void testCaseFromSwitch() throws IncorrectOperationException {
    // Inspired by IDEADEV-22920
    getSettings().INDENT_CASE_FROM_SWITCH = false;
    doTextTest("class Foo{\n" +
               "void foo () {\n" +
               "switch(someValue) {\n" +
               " // This comment is correctly not-indented\n" +
               " case 1:\n" +
               "    doSomething();\n" +
               "    break;\n" +
               "\n" +
               " // This comment should not be indented, but it is\n" +
               " case 2:\n" +
               "    doSomethingElse();\n" +
               "    break;\n" +
               "}\n" +
               "}\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        switch (someValue) {\n" +
                    "        // This comment is correctly not-indented\n" +
                    "        case 1:\n" +
                    "            doSomething();\n" +
                    "            break;\n" +
                    "\n" +
                    "        // This comment should not be indented, but it is\n" +
                    "        case 2:\n" +
                    "            doSomethingElse();\n" +
                    "            break;\n" +
                    "        }\n" +
                    "    }\n" +
                    "}");
  }
}
