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
package com.intellij.java.psi.formatter.java;

import com.intellij.formatting.FormatterTestUtils;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public class JavaIndenterTest extends AbstractJavaFormatterTest {

  private static final String CARET_TOKEN = "<caret>";

  public void testIndentAlignedMethodParameter() {
    // Inspired by IDEA-22020
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    String initial =
      """
        class BrokenAlignment {
            public
        \tstatic int foo(String a, String b, String c,
                String d) {
                return -1;
            }
        }""";

    int start = initial.indexOf("        String d");
    int end = initial.indexOf("\n", start);
    myTextRange = new TextRange(start, end);

    doTextTest(initial,
               """
                 class BrokenAlignment {
                     public
                 \tstatic int foo(String a, String b, String c,
                                    String d) {
                         return -1;
                     }
                 }"""
    );
  }

  public void testMethodBodyShiftedToComment() {
    // Inspired by IDEA-53778

    doTextTest(
      """
        class Test {
           // some comment
                public void doSmth(int[] p) {
        <caret>
                }}""",

      """
        class Test {
           // some comment
                public void doSmth(int[] p) {
                   \s
                }}""");
  }

  @Override
  public void doTextTest(@NotNull String text, @NotNull String textAfter) throws IncorrectOperationException {
    doTextTest(FormatterTestUtils.Action.INDENT, adjustTextIfNecessary(text), textAfter);
  }

  private String adjustTextIfNecessary(String text) {
    int caretIndex = text.indexOf(CARET_TOKEN);
    if (caretIndex < 0) {
      return text;
    }

    if (caretIndex < text.length() && text.indexOf(CARET_TOKEN, caretIndex + 1) >= 0) {
      fail(String.format("Invalid indentation test 'before' text - it contains more than one caret meta-token (%s). Text: %n%s",
                         CARET_TOKEN, text));
    }
    myTextRange = new TextRange(caretIndex, caretIndex);
    return text.substring(0, caretIndex) + text.substring(caretIndex + CARET_TOKEN.length());
  }
}