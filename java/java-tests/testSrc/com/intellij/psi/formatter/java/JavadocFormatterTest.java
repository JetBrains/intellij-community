/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
 * @author Denis Zhdanov
 * @since 1/18/11 3:11 PM
 */
public class JavadocFormatterTest extends AbstractJavaFormatterTest {

  public void testRightMargin() throws Exception {
    getSettings().WRAP_COMMENTS = true;
    getSettings().RIGHT_MARGIN = 35;//      |
    doTextTest(
      "/** Here is one-line java-doc comment */" +
      "class Foo {\n" +
      "}",
      "/**\n" +
      " * Here is one-line java-doc\n" +
      " * comment\n" +
      " */\n" +
      "class Foo {\n" +
      "}");

  }

  public void testLineFeedsArePreservedDuringWrap() {
    // Inspired by IDEA-61895
    getSettings().WRAP_COMMENTS = true;
    getSettings().JD_PRESERVE_LINE_FEEDS = true;
    getSettings().RIGHT_MARGIN = 48;
    
    doTextTest(
      "/**\n" +
      " * This is a long comment that spans more than one\n" +
      " * line\n" +
      " */\n" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " * This is a long comment that spans more than\n" +
      " * one\n" +
      " * line\n" +
      " */\n" +
      "class Test {\n" +
      "}"
    );
  }
  
  public void testSCR11296() throws Exception {
    final CodeStyleSettings settings = getSettings();
    settings.RIGHT_MARGIN = 50;
    settings.WRAP_COMMENTS = true;
    settings.ENABLE_JAVADOC_FORMATTING = true;
    settings.JD_P_AT_EMPTY_LINES = false;
    settings.JD_KEEP_EMPTY_LINES = false;
    doTest();
  }

  public void testSCR2632() throws Exception {
    getSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().RIGHT_MARGIN = 20;

    doTextTest("/**\n" + " * <p />\n" + " * Another paragraph of the description placed after blank line.\n" + " */\n" + "class A{}",
               "/**\n" +
               " * <p/>\n" +
               " * Another paragraph\n" +
               " * of the description\n" +
               " * placed after\n" +
               " * blank line.\n" +
               " */\n" +
               "class A {\n" +
               "}");
  }
  
  public void testParagraphTagGeneration() {
    // Inspired by IDEA-61811
    getSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().JD_P_AT_EMPTY_LINES = true;
    doTextTest(
      "/**\n" +
      " * line 1\n" +
      " *\n" +
      " * line 2\n" +
      " * <pre>\n" +
      " *   line 3\n" +
      " *\n" +
      " *   line 4\n" +
      " * </pre>\n" +
      " */\n" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " * line 1\n" +
      " * <p/>\n" +
      " * line 2\n" +
      " * <pre>\n" +
      " *   line 3\n" +
      " *\n" +
      " *   line 4\n" +
      " * </pre>\n" +
      " */\n" +
      "class Test {\n" +
      "}"
    );
  }
  
  public void testWrappedParameterDescription() throws Exception {
    // Inspired by IDEA-13072
    getSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().JD_PARAM_DESCRIPTION_ON_NEW_LINE = true;
    doClassTest(
      "/**\n" +
      " * test description\n" +
      " * @param first first description\n" +
      " * @param second\n" +
      " * @param third third\n" +
      " *              description\n" +
      " * @param forth\n" +
      " *          forth description\n" +
      " */\n" +
      "void test(int first, int second, int third, int forth) {\n" +
      "}",
      "/**\n" +
      " * test description\n" +
      " *\n" +
      " * @param first\n" +
      " *         first description\n" +
      " * @param second\n" +
      " * @param third\n" +
      " *         third description\n" +
      " * @param forth\n" +
      " *         forth description\n" +
      " */\n" +
      "void test(int first, int second, int third, int forth) {\n" +
      "}"
    );
  }
}
