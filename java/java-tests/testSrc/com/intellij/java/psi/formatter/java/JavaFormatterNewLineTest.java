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
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold specific java formatting tests for {@code 'Place on New Line'} settings (
 * {@code Project Settings - Code Style - Alignment and Braces - Place on New Line}).
 */
@TestApplication
public class JavaFormatterNewLineTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testAutomaticElseWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ELSE_ON_NEW_LINE = true;

    formatter.doMethodTest(
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

  @Test
  public void testAutomaticElseUnwrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ELSE_ON_NEW_LINE = false;
    formatter.getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    formatter.doMethodTest(
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

  @Test
  public void testAutomaticCatchWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CATCH_ON_NEW_LINE = true;

    formatter.doMethodTest(
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

  @Test
  public void testAutomaticCatchUnwrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CATCH_ON_NEW_LINE = false;
    formatter.getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    formatter.doMethodTest(
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

  @Test
  public void testAutomaticFinallyWrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().FINALLY_ON_NEW_LINE = true;

    formatter.doMethodTest(
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

  @Test
  public void testAutomaticFinallyUnwrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().FINALLY_ON_NEW_LINE = false;
    formatter.getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    formatter.doMethodTest(
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

  @Test
  public void testAutomaticCatchFinallyUnwrapping() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-47809
    formatter.getSettings().CATCH_ON_NEW_LINE = false;
    formatter.getSettings().FINALLY_ON_NEW_LINE = false;
    formatter.getSettings().KEEP_LINE_BREAKS = true;

    formatter.doMethodTest(
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

  @Test
  public void testClassInitializationBlockBracesPlacement() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-54191
    formatter.getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    formatter.doMethodTest(
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

  @Test
  public void testBlockOfMethodWithAnnotatedParameter() {
    // Inspired by IDEA-17870
    formatterFixture.get().doClassTest("public Test(@Qualifier(\"blah\") AType blah){}", "public Test(@Qualifier(\"blah\") AType blah) {\n" + "}");
  }

  @Test
  public void testArrayInitializer() throws IncorrectOperationException {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEADEV-6787
    formatter.getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.getSettings().ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true;
    formatter.getSettings().ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true;
    formatter.doTextTest(
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

  @Test
  public void testSimpleAnnotatedMethodAndBraceOnNextLineStyle() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-53542
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().KEEP_LINE_BREAKS = true;
    formatter.getSettings().KEEP_BLANK_LINES_IN_CODE = 2;

    String methodWithAnnotation = "@Override\n" +
                                  "void foo() {}";

    String methodWithAnnotationAndVisibility = "@Override\n" +
                                               "public void foo() {}";

    // Don't expect that simple method to be spread on multiple lines.
    formatter.doClassTest(methodWithAnnotation, methodWithAnnotation);
    formatter.doClassTest(methodWithAnnotationAndVisibility, methodWithAnnotationAndVisibility);
  }

  @Test
  public void testMoveSimpleMethodBodyOnNewLineWhenPresent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doClassTest("""
                     public void foo() {int x = 1;}
                     """,
                """
                  public void foo() {
                      int x = 1;
                  }
                  """);
  }

  @Test
  public void testDoNotMoveSimpleMethodBodyOnNewLineWhenAbsent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doClassTest("""
                     public void foo() {}
                     """,
                """
                  public void foo() { }
                  """);
  }

  @Test
  public void testDoNotMoveSimpleMethodBodyOnNewLineWhenAbsentAndSettingsDisabled() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = false;
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doClassTest("""
                     public void foo() {int x = 1;}
                     """,
                """
                  public void foo() { int x = 1; }
                  """);
  }



  @Test
  public void testMoveSimpleCodeBlockOnNewLineWhenPresent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.doMethodTest("""
                   Integer x = 1;
                   switch (x) {
                      case 1 -> {return;}
                      default -> {return;}
                   }
                   if (x == 1) {return;}
                   while (true) {return;}
                   for (;;) {return;}
                   """,
                 """
                   Integer x = 1;
                   switch (x) {
                       case 1 -> {
                           return;
                       }
                       default -> {
                           return;
                       }
                   }
                   if (x == 1) {
                       return;
                   }
                   while (true) {
                       return;
                   }
                   for (; ; ) {
                       return;
                   }
                   """);
  }

  @Test
  public void testDoNotMoveSimpleCodeBlockOnNewLineWhenAbsent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doMethodTest("""
                   Integer x = 1;
                   switch (x) {
                      case 1 -> {}
                      default -> {}
                   }
                   if (x == 1) {}
                   while (true) {}
                   for (;;) {}
                   """,
                 """
                   Integer x = 1;
                   switch (x) {
                       case 1 -> { }
                       default -> { }
                   }
                   if (x == 1) { }
                   while (true) { }
                   for (; ; ) { }
                   """);
  }

  @Test
  public void testDoNotMoveSimpleCodeBlockOnNewLineWhenPresentAndSettingsDisabled() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = false;
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doMethodTest("""
                   Integer x = 1;
                   switch (x) {
                      case 1 -> {return;}
                      default -> {return;}
                   }
                   if (x == 1) {return;}
                   while (true) {return;}
                   for (;;) {return;}
                   """,
                 """
                   Integer x = 1;
                   switch (x) {
                       case 1 -> { return; }
                       default -> { return; }
                   }
                   if (x == 1) { return; }
                   while (true) { return; }
                   for (; ; ) { return; }
                   """);
  }

  @Test
  public void testMoveSimpleLambdaOnNewLineWhenPresent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    formatter.doMethodTest("""
                   Runnable r = () -> { return; };
                   """,
                 """
                   Runnable r = () -> {
                       return;
                   };
                   """);
  }

  @Test
  public void testDoNotMoveSimpleLambdaOnNewLineWhenAbsent() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doMethodTest("""
                   Runnable r = () -> {};
                   """,
                 """
                   Runnable r = () -> { };
                   """);
  }

  @Test
  public void testDoNotMoveSimpleLambdaOnNewLineWhenPresentAndSettingsDisabled() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = false;
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doMethodTest("""
                   Runnable r = () -> { return; };
                   """,
                 """
                   Runnable r = () -> { return; };
                   """);
  }

  @Test
  public void testDoNotMoveSimpleLambdaOnNewLineWithoutCodeBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getJavaSettings().NEW_LINE_WHEN_BODY_IS_PRESENTED = true;
    formatter.getSettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.doMethodTest("""
                   Runnable r = () -> foo();
                   """,
                 """
                   Runnable r = () -> foo();
                   """);
  }
}
