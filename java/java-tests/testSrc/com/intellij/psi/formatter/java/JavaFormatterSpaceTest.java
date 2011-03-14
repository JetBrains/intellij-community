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

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

/**
 * Is intended to hold specific java formatting tests for 'spacing' settings.
 *
 * @author Denis Zhdanov
 * @since Apr 29, 2010 5:50:34 PM
 */
public class JavaFormatterSpaceTest extends AbstractJavaFormatterTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  public void testSpacingBetweenTypeParameters() throws Exception {
    // Implied by IDEADEV-3666
    getSettings().SPACE_AFTER_COMMA = true;

    doTextTest("class Foo {\n" + "Map<String,String> map() {}\n" + "}",
               "class Foo {\n" + "    Map<String, String> map() {\n" + "    }\n" + "}");
  }

  public void testSpaceBeforeAnnotationParamArray() {
    // Inspired by IDEA-24329
    getSettings().SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true;

    String text =
      "@SuppressWarnings( {\"ALL\"})\n" +
      "public class FormattingTest {\n" +
      "}";

    // Don't expect the space to be 'ate'
    doTextTest(text, text);
  }

  public void testCommaInTypeArguments() {
    // Inspired by IDEA-31681
    getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = false;

    String initial =
      "interface TestInterface<A,B> {\n" +
      "\n" +
      "    <X,Y> void foo(X x, Y y);\n" +
      "}\n" +
      "\n" +
      "public class FormattingTest implements TestInterface<String,Integer> {\n" +
      "\n" +
      "    public <X,Y> void foo(X x, Y y) {\n" +
      "        Map<String,Integer> map = new HashMap<String,Integer>();\n" +
      "    }\n" +
      "}";

    doTextTest(initial, initial); // Don't expect the comma to be inserted

    getSettings().SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
    String formatted =
      "interface TestInterface<A, B> {\n" +
      "\n" +
      "    <X, Y> void foo(X x, Y y);\n" +
      "}\n" +
      "\n" +
      "public class FormattingTest implements TestInterface<String, Integer> {\n" +
      "\n" +
      "    public <X, Y> void foo(X x, Y y) {\n" +
      "        Map<String, Integer> map = new HashMap<String, Integer>();\n" +
      "    }\n" +
      "}";
    doTextTest(initial, formatted); // Expect the comma to be inserted between type arguments
  }

  public void testUnaryOperators() {
    // Inspired by IDEA-52127
    getSettings().SPACE_AROUND_UNARY_OPERATOR = false;

    String initial =
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        int i = 1;\n" +
      "        System.out.println(-i);\n" +
      "        System.out.println(+i);\n" +
      "        System.out.println(++i);\n" +
      "        System.out.println(i++);\n" +
      "        System.out.println(--i);\n" +
      "        System.out.println(i--);\n" +
      "        boolean b = true;\n" +
      "        System.out.println(!b);\n" +
      "    }\n" +
      "}";

    doTextTest(initial, initial); // Don't expect spaces to be inserted after unary operators

    getSettings().SPACE_AROUND_UNARY_OPERATOR = true;
    String formatted =
      "public class FormattingTest {\n" +
      "    public void foo() {\n" +
      "        int i = 1;\n" +
      "        System.out.println(- i);\n" +
      "        System.out.println(+ i);\n" +
      "        System.out.println(++ i);\n" +
      "        System.out.println(i++);\n" +
      "        System.out.println(-- i);\n" +
      "        System.out.println(i--);\n" +
      "        boolean b = true;\n" +
      "        System.out.println(! b);\n" +
      "    }\n" +
      "}";
    doTextTest(initial, formatted); // Expect spaces to be inserted after unary operators
  }

  public void testJavadocMethodParams() {
    // Inspired by IDEA-42167
    getSettings().SPACE_AFTER_COMMA = false;

    String initial =
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test,   new      Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}";

    // Expect single space to left between 'new' and Object[0].
    doTextTest(initial,
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test,new Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}");

    // Expect space to be inserted between ',' and 'new'.
    getSettings().SPACE_AFTER_COMMA = true;
    doTextTest(initial,
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test, new Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}");

    // Expect space to be inserted between 'test' and ','.
    getSettings().SPACE_BEFORE_COMMA = true;
    doTextTest(initial,
      "public class FormattingTest {\n" +
      "    /**\n" +
      "     * This is a convenience method for {@code doTest(test , new Object[0]);}\n" +
      "     */\n" +
      "    void doTest() {\n" +
      "    }\n" +
      "}");
  }

  public void testSpaceWithArrayBrackets() throws Exception {
    // Inspired by IDEA-58510
    getSettings().SPACE_WITHIN_BRACKETS = true;
    doMethodTest(
      "int[] i = new int[1]\n" +
      "i[0] = 1;\n" +
      "int[] i2 = new int[]{1}",
      "int[] i = new int[ 1 ]\n" +
      "i[ 0 ] = 1;\n" +
      "int[] i2 = new int[]{1}"
    );
  }
  
  public void testSpaceBeforeElse() throws Exception {
    // Inspired by IDEA-58068
    getSettings().ELSE_ON_NEW_LINE = false;
    
    getSettings().SPACE_BEFORE_ELSE_KEYWORD = false;
    doMethodTest(
      "if (true) {\n" +
      "} else {\n" +
      "}",
      "if (true) {\n" +
      "}else {\n" +
      "}"
    );

    getSettings().SPACE_BEFORE_ELSE_KEYWORD = true;
    doMethodTest(
      "if (true) {\n" +
      "}else {\n" +
      "}",
      "if (true) {\n" +
      "} else {\n" +
      "}"
    );
  }

  public void testSpaceBeforeWhile() throws Exception {
    // Inspired by IDEA-58068
    getSettings().WHILE_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_WHILE_KEYWORD = false;
    doMethodTest(
      "do {\n" +
      "} while (true);",
      "do {\n" +
      "}while (true);"
    );

    getSettings().SPACE_BEFORE_WHILE_KEYWORD = true;
    doMethodTest(
      "do {\n" +
      "}while (true);",
      "do {\n" +
      "} while (true);"
    );
  }

  
  public void testSpaceBeforeCatch() throws Exception {
    // Inspired by IDEA-58068
    getSettings().CATCH_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_CATCH_KEYWORD = false;
    doMethodTest(
      "try {\n" +
      "} catch (Exception e) {\n" +
      "}",
      "try {\n" +
      "}catch (Exception e) {\n" +
      "}"
    );

    getSettings().SPACE_BEFORE_CATCH_KEYWORD = true;
    doMethodTest(
      "try {\n" +
      "}catch (Exception e) {\n" +
      "}",
      "try {\n" +
      "} catch (Exception e) {\n" +
      "}"
    );
  }

  public void testSpaceBeforeFinally() throws Exception {
    // Inspired by IDEA-58068
    getSettings().FINALLY_ON_NEW_LINE = false;

    getSettings().SPACE_BEFORE_FINALLY_KEYWORD = false;
    doMethodTest(
      "try {\n" +
      "} finally {\n" +
      "}",
      "try {\n" +
      "}finally {\n" +
      "}"
    );

    getSettings().SPACE_BEFORE_FINALLY_KEYWORD = true;
    doMethodTest(
      "try {\n" +
      "}finally {\n" +
      "}",
      "try {\n" +
      "} finally {\n" +
      "}"
    );
  }

  public void testEmptyIterationAtFor() throws Exception {
    // Inspired by IDEA-58293

    getSettings().SPACE_AFTER_SEMICOLON = true;
    getSettings().SPACE_WITHIN_FOR_PARENTHESES = false;
    
    doMethodTest(
      "for (   ; ;         )",
      "for (; ; )"
    );
  }

  public void testSpacesInDisjunctiveType() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().CATCH_ON_NEW_LINE = false;

    getSettings().SPACE_AROUND_BITWISE_OPERATORS = true;
    doMethodTest("try { } catch (E1|E2 e) { }",
                 "try { } catch (E1 | E2 e) { }");

    getSettings().SPACE_AROUND_BITWISE_OPERATORS = false;
    doMethodTest("try { } catch (E1 | E2 e) { }",
                 "try { } catch (E1|E2 e) { }");
  }

  public void testSpacesBeforeResourceList() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_BEFORE_TRY_PARENTHESES = true;
    getSettings().SPACE_BEFORE_TRY_LBRACE = true;
    doMethodTest("try(AutoCloseable r = null){ }",
                 "try (AutoCloseable r = null) { }");

    getSettings().SPACE_BEFORE_TRY_PARENTHESES = false;
    getSettings().SPACE_BEFORE_TRY_LBRACE = false;
    doMethodTest("try (AutoCloseable r = null) { }",
                 "try(AutoCloseable r = null){ }");
  }

  public void testSpacesWithinResourceList() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_WITHIN_TRY_PARENTHESES = false;
    doMethodTest("try (  R r = null  ) { }",
                 "try (R r = null) { }");
    getSettings().SPACE_AFTER_SEMICOLON = false;
    doMethodTest("try (  R r1 = null    ; R r2 = null; ) { }",
                 "try (R r1 = null;R r2 = null;) { }");

    getSettings().SPACE_WITHIN_TRY_PARENTHESES = true;
    doMethodTest("try (R r = null) { }",
                 "try ( R r = null ) { }");
    getSettings().SPACE_AFTER_SEMICOLON = true;
    doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try ( R r1 = null; R r2 = null; ) { }");
  }

  public void testSpacesBetweenResources() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_BEFORE_SEMICOLON = false;
    getSettings().SPACE_AFTER_SEMICOLON = true;
    doMethodTest("try (R r1 = null    ; R r2 = null;) { }",
                 "try (R r1 = null; R r2 = null; ) { }");

    getSettings().SPACE_BEFORE_SEMICOLON = true;
    getSettings().SPACE_AFTER_SEMICOLON = false;
    doMethodTest("try (R r1 = null;   R r2 = null;) { }",
                 "try (R r1 = null ;R r2 = null ;) { }");
  }

  public void testSpacesInResourceAssignment() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
    doMethodTest("try (R r=null) { }",
                 "try (R r = null) { }");

    getSettings().SPACE_AROUND_ASSIGNMENT_OPERATORS = false;
    doMethodTest("try (R r =  null) { }",
                 "try (R r=null) { }");
  }
}
