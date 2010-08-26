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

import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * Is intended to hold specific java formatting tests for 'braces placement' settings (
 * <code>Project Settings - Code Style - Alignment and Braces</code>).
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:39:24 PM
 */
public class JavaFormatterBracesTest extends AbstractJavaFormatterTest {

  public void testBracePositioningAtPreviousLine() throws Exception {
    // Inspired by IDEADEV-18529
    doTextTest(
      "public class TestBed\n" +
      "{\n" +
      "    public void methodOne()\n" +
      "    {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    @SomeAnnotation\n" +
      "            <T extends Comparable> void methodTwo(T item) {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    private void methodThree(String s) {\n" +
      "        //code...\n" +
      "    }\n" +
      "}",

      "public class TestBed {\n" +
      "    public void methodOne() {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    @SomeAnnotation\n" +
      "    <T extends Comparable> void methodTwo(T item) {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    private void methodThree(String s) {\n" +
      "        //code...\n" +
      "    }\n" +
      "}");
  }

  public void testSimpleBlockInOneLinesAndForceBraces() throws Exception {
    // Inspired by IDEA-19328
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().IF_BRACE_FORCE = CodeStyleSettings.FORCE_BRACES_ALWAYS;

    doMethodTest(
      "if (x > y) System.out.println(\"foo!\");",

      "if (x > y) { System.out.println(\"foo!\"); }"
    );
  }

  public void testEnforcingBracesForExpressionEndingWithLineComment() throws Exception {
    // Inspired by IDEA-57936
    getSettings().IF_BRACE_FORCE = CodeStyleSettings.FORCE_BRACES_ALWAYS;

    doMethodTest(
      "if (true) i = 1; // Cool if\n" +
      "else i = 2;",

      "if (true) {\n" +
      "    i = 1; // Cool if\n" +
      "}\n" +
      "else {\n" +
      "    i = 2;\n" +
      "}"
    );
  }
}
