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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold specific java formatting tests for {@code 'Place on New Line'} settings (
 * {@code Project Settings - Code Style - Alignment and Braces - Place on New Line}).
 */
public class JavaFormatterNewLineTest extends AbstractJavaFormatterTest {

  public void testAutomaticElseWrapping() {
    getSettings().ELSE_ON_NEW_LINE = true;

    doMethodTest(
      """
        if (b) {
        } else {
        }""",

      """
        if (b) {
        }
        else {
        }"""
    );
  }

  public void testAutomaticElseUnwrapping() {
    getSettings().ELSE_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    doMethodTest(
      """
        if (b) {
        }
        else {
        }""",

      """
        if (b) {
        } else {
        }"""
    );
  }

  public void testAutomaticCatchWrapping() {
    getSettings().CATCH_ON_NEW_LINE = true;

    doMethodTest(
      """
        try {
        } catch (Exception e) {
        }""",

      """
        try {
        }
        catch (Exception e) {
        }"""
    );
  }

  public void testAutomaticCatchUnwrapping() {
    getSettings().CATCH_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    doMethodTest(
      """
        try {
        }
        catch (Exception e) {
        }""",

      """
        try {
        } catch (Exception e) {
        }"""
    );
  }

  public void testAutomaticFinallyWrapping() {
    getSettings().FINALLY_ON_NEW_LINE = true;

    doMethodTest(
      """
        try {
        } finally {
        }""",

      """
        try {
        }
        finally {
        }"""
    );
  }

  public void testAutomaticFinallyUnwrapping() {
    getSettings().FINALLY_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    doMethodTest(
      """
        try {
        }
        finally {
        }""",

      """
        try {
        } finally {
        }"""
    );
  }

  public void testAutomaticCatchFinallyUnwrapping() {
    // Inspired by IDEA-47809
    getSettings().CATCH_ON_NEW_LINE = false;
    getSettings().FINALLY_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    doMethodTest(
      """
        try {
        }
        catch (Exception e) {
        }
        finally {
        }""",

      """
        try {
        } catch (Exception e) {
        } finally {
        }"""
    );
  }

  public void testClassInitializationBlockBracesPlacement() {
    // Inspired by IDEA-54191
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doMethodTest(
      "new Expectations() {\n" +
      "    {foo();}};",

      """
        new Expectations() {
            {
                foo();
            }
        };"""
    );
  }

  public void testBlockOfMethodWithAnnotatedParameter() {
    // Inspired by IDEA-17870
    doClassTest("public Test(@Qualifier(\"blah\") AType blah){}", "public Test(@Qualifier(\"blah\") AType blah) {\n" + "}");
  }

  public void testArrayInitializer() throws IncorrectOperationException {
    // Inspired by IDEADEV-6787
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true;
    getSettings().ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true;
    doTextTest(
      """
        public @interface Ann
        {
        int[] x = { 1, 2 };

        Mode[] modes () default { @Mode(value = 1), @Mode(value = 2) };
        }""",

      """
        public @interface Ann {
            int[] x = {
                    1,
                    2
            };

            Mode[] modes() default {
                    @Mode(value = 1),
                    @Mode(value = 2)
            };
        }"""
    );
  }

  public void testSimpleAnnotatedMethodAndBraceOnNextLineStyle() {
    // Inspired by IDEA-53542
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().KEEP_LINE_BREAKS = true;
    getSettings().KEEP_BLANK_LINES_IN_CODE = 2;

    String methodWithAnnotation = "@Override\n" +
                                  "void foo() {}";

    String methodWithAnnotationAndVisibility = "@Override\n" +
                                               "public void foo() {}";

    // Don't expect that simple method to be spread on multiple lines.
    doClassTest(methodWithAnnotation, methodWithAnnotation);
    doClassTest(methodWithAnnotationAndVisibility, methodWithAnnotationAndVisibility);
  }

  public void testMoveSimpleMethodBodyOnNewLineWhenPresent() {
    getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    doClassTest("""
                     public void foo() {int x = 1;}
                     """,
                """
                  public void foo() {
                      int x = 1;
                  }
                  """);
  }

  public void testDoNotMoveSimpleMethodBodyOnNewLineWhenAbsent() {
    getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    doClassTest("""
                     public void foo() {}
                     """,
                """
                  public void foo() { }
                  """);
  }

  public void testDoNotMoveSimpleMethodBodyOnNewLineWhenAbsentAndSettingsDisabled() {
    getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = false;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    doClassTest("""
                     public void foo() {int x = 1;}
                     """,
                """
                  public void foo() { int x = 1; }
                  """);
  }
}
