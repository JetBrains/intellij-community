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

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Denis Zhdanov
 * @since May 11, 2010 5:30:28 PM
 */
public class AutoIndentLinesTest extends AbstractJavaFormatterTest {

  public void testIndentAlignedMethodParameter() {
    // Inspired by IDEA-22020
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    String initial =
      "class BrokenAlignment {\n" +
      "    public\n" +
      "\tstatic int foo(String a, String b, String c,\n" +
      "        String d) {\n" +
      "        return -1;\n" +
      "    }\n" +
      "}";

    int start = initial.indexOf("        String d");
    int end = initial.indexOf("\n", start);
    myTextRange = new TextRange(start, end);

    doTextTest(Action.INDENT, initial,                     
      "class BrokenAlignment {\n" +
      "    public\n" +
      "\tstatic int foo(String a, String b, String c,\n" +
      "                   String d) {\n" +
      "        return -1;\n" +
      "    }\n" +
      "}"
    );
  }

  @Override
  public void doTextTest(String text, String textAfter) throws IncorrectOperationException {
    doTextTest(Action.INDENT, text, textAfter);
  }
}
