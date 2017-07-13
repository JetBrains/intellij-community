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

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold specific java formatting tests for {@code 'Place on New Line'} settings (
 * {@code Project Settings - Code Style - Alignment and Braces - Place on New Line}).
 *
 * @author Denis Zhdanov
 * @since Apr 28, 2010 12:12:13 PM
 */
public class JavaFormatterNewLineTest extends AbstractJavaFormatterTest {

  public void testAutomaticElseWrapping() {
    getSettings().ELSE_ON_NEW_LINE = true;

    doMethodTest(
      "if (b) {\n" +
      "} else {\n" +
      "}",

      "if (b) {\n" +
      "}\n" +
      "else {\n" +
      "}"
    );
  }

  public void testAutomaticElseUnwrapping() {
    getSettings().ELSE_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    doMethodTest(
      "if (b) {\n" +
      "}\n" +
      "else {\n" +
      "}",

      "if (b) {\n" +
      "} else {\n" +
      "}"
    );
  }

  public void testAutomaticCatchWrapping() {
    getSettings().CATCH_ON_NEW_LINE = true;

    doMethodTest(
      "try {\n" +
      "} catch (Exception e) {\n" +
      "}",

      "try {\n" +
      "}\n" +
      "catch (Exception e) {\n" +
      "}"
    );
  }

  public void testAutomaticCatchUnwrapping() {
    getSettings().CATCH_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    doMethodTest(
      "try {\n" +
      "}\n" +
      "catch (Exception e) {\n" +
      "}",

      "try {\n" +
      "} catch (Exception e) {\n" +
      "}"
    );
  }

  public void testAutomaticFinallyWrapping() {
    getSettings().FINALLY_ON_NEW_LINE = true;

    doMethodTest(
      "try {\n" +
      "} finally {\n" +
      "}",

      "try {\n" +
      "}\n" +
      "finally {\n" +
      "}"
    );
  }

  public void testAutomaticFinallyUnwrapping() {
    getSettings().FINALLY_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    // Inspired by IDEA-47809
    doMethodTest(
      "try {\n" +
      "}\n" +
      "finally {\n" +
      "}",

      "try {\n" +
      "} finally {\n" +
      "}"
    );
  }

  public void testAutomaticCatchFinallyUnwrapping() {
    // Inspired by IDEA-47809
    getSettings().CATCH_ON_NEW_LINE = false;
    getSettings().FINALLY_ON_NEW_LINE = false;
    getSettings().KEEP_LINE_BREAKS = true;

    doMethodTest(
      "try {\n" +
      "}\n" +
      "catch (Exception e) {\n" +
      "}\n" +
      "finally {\n" +
      "}",

      "try {\n" +
      "} catch (Exception e) {\n" +
      "} finally {\n" +
      "}"
    );
  }

  public void testClassInitializationBlockBracesPlacement() {
    // Inspired by IDEA-54191
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 4;
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doMethodTest(
      "new Expectations() {\n" +
      "    {foo();}};",

      "new Expectations() {\n" +
      "    {\n" +
      "        foo();\n" +
      "    }\n" +
      "};"
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
               "public @interface Ann\n" +
               "{\n" +
               "int[] x = { 1, 2 };\n" +
               "\n" +
               "Mode[] modes () default { @Mode(value = 1), @Mode(value = 2) };\n" +
               "}",

               "public @interface Ann {\n" +
               "    int[] x = {\n" +
               "            1,\n" +
               "            2\n" +
               "    };\n" +
               "\n" +
               "    Mode[] modes() default {\n" +
               "            @Mode(value = 1),\n" +
               "            @Mode(value = 2)\n" +
               "    };\n" +
               "}"
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
}
