/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblematicWhitespaceInspection;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("InfiniteRecursion")
public class ProblematicWhitespaceInspectionTest extends LightJavaInspectionTestCase {

  public void testHtml() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(HtmlFileType.INSTANCE).USE_TAB_CHARACTER = false;
    myFixture.configureByText("X.html", """
      <warning descr="File 'X.html' uses tabs for indentation"><html>
      \t<body></body>
      </html></warning>""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testTabsInFile() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    doTest("""
             /*File 'X.java' uses tabs for indentation*/class X {
             \tString s;
             }
             /**/""");
  }

  public void testTabsInFile2() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    doTest("""
             class X {
             \tString s;
             }
             """);
  }

  public void testSpacesInFile() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    doTest("""
             /*File 'X.java' uses spaces for indentation*/class X {
               String s;
             }
             /**/""");
  }

  public void testSpacesInFile2() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    doTest("""
             class X {
               String s;
             }
             """);
  }

  public void testSmartTabsInFile() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    doTest("""
             /*File 'X.java' uses spaces for indentation*/class X {
               \tString s;
             }
             /**/""");
  }

  public void testSmartTabsInFile2() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    doTest("""
             class X {
             \tvoid aaa(boolean a, boolean b, boolean c) {
             \t\taaa(true,
             \t\t    true,
             \t\t    true);
             \t}
             }
             """);
  }

  public void testSmartTabsInFile3() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    doTest("""
             /*File 'X.java' uses spaces for indentation*/class X {
             \tvoid aaa(boolean a, boolean b, boolean c) {
             \t\taaa(true,
             \t \t    true,
             \t\t    true);
             \t}
             }
             /**/""");
  }

  public void testSmartTabsInFileWithoutBinaryExpressionMultilineAlignment() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    doTest("""
             class X {static{
             \tSystem.out.println("asdf" +
             \t\t\t                   "asdf");
             }}""");
  }

  public void testSuppression1() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    myFixture.configureByText("X.html", """
      <!--suppress ProblematicWhitespace --><html>
      \t<body></body>
      </html>""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testSuppression2() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    myFixture.configureByText("x.css", """
      /*noinspection ProblematicWhitespace*/
      div {
       font-family: arial, helvetica;
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testSuppression3() {
    final CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    doTest("""
             @SuppressWarnings("ProblematicWhitespace") class X {
             \tString s;
             }
             """);
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ProblematicWhitespaceInspection();
  }
}
