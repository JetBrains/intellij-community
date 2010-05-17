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

/**
 * Is intended to hold specific java formatting tests for 'spacing' settings.
 *
 * @author Denis Zhdanov
 * @since Apr 29, 2010 5:50:34 PM
 */
public class JavaFormatterSpaceTest extends AbstractJavaFormatterTest {

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
}
