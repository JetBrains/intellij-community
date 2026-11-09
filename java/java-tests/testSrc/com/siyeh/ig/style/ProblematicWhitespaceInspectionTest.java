// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.ProblematicWhitespaceInspection;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@SuppressWarnings("InfiniteRecursion")
public class ProblematicWhitespaceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ProblematicWhitespaceInspection());

  @Test
  public void testHtml() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(HtmlFileType.INSTANCE).USE_TAB_CHARACTER = false;
    myFixture.configureByText("X.html", """
      <warning descr="File 'X.html' uses tabs for indentation"><html>
      \t<body></body>
      </html></warning>""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testTabsInFile() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    inspectionFixture.get().doTest("""
             /*File 'X.java' uses tabs for indentation*/class X {
             \tString s;
             }
             /**/""");
  }

  @Test
  public void testTabsInFile2() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    inspectionFixture.get().doTest("""
             class X {
             \tString s;
             }
             """);
  }

  @Test
  public void testTextBlock() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    CommonCodeStyleSettings.IndentOptions indentOptions = CodeStyle.getSettings(myFixture.getProject()).getIndentOptions(JavaFileType.INSTANCE);
    indentOptions.USE_TAB_CHARACTER = true;
    inspectionFixture.get().doTest("""
             class X {
             \tString s = \"""
             \t\t{
             \t\t  indented
             \t\t}
             \t\t\""";
             }
             """);

    // warn about tabs because they should be escaped
    indentOptions.USE_TAB_CHARACTER = false;
    inspectionFixture.get().doTest("""
             /*File 'X.java' uses tabs for indentation*/class X {
               String s = \"""
                 {
                 \tindented
                 }
                 \""";
             }
             /**/""");
  }

  @Test
  public void testDocComments() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    myFixture.configureByText("X.java", """
             /**
              * This is class X.
              */
             class X {
             \t/**
             \t * This is field s.
             \t */
             \tString s;
             }
             """);
    myFixture.testHighlighting(true, false, false);

    myFixture.configureByText("Main.java", """
      public class Main {
      \t/**
      \t * something
      \t */
      \tpublic static void main(String[] args) {
      \t\tSystem.out.print("Hello and welcome!");
      \t}
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testSpacesInFile() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    inspectionFixture.get().doTest("""
             /*File 'X.java' uses spaces for indentation*/class X {
               String s;
             }
             /**/""");
  }

  @Test
  public void testSpacesInFile2() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    inspectionFixture.get().doTest("""
             class X {
               String s;
             }
             """);
  }

  @Test
  public void testSmartTabsInFile() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    inspectionFixture.get().doTest("""
             /*File 'X.java' uses spaces for indentation*/class X {
               \tString s;
             }
             /**/""");
  }

  @Test
  public void testSmartTabsInFile2() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    inspectionFixture.get().doTest("""
             class X {
             \tvoid aaa(boolean a, boolean b, boolean c) {
             \t\taaa(true,
             \t\t    true,
             \t\t    true);
             \t}
             }
             """);
  }

  @Test
  public void testSmartTabsInFile3() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    inspectionFixture.get().doTest("""
             /*File 'X.java' uses spaces for indentation*/class X {
             \tvoid aaa(boolean a, boolean b, boolean c) {
             \t\taaa(true,
             \t \t    true,
             \t\t    true);
             \t}
             }
             /**/""");
  }

  @Test
  public void testSmartTabsInFileWithoutBinaryExpressionMultilineAlignment() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    inspectionFixture.get().doTest("""
             class X {static{
             \tSystem.out.println("asdf" +
             \t\t\t                   "asdf");
             }}""");
  }

  @Test
  public void testSuppression1() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    myFixture.configureByText("X.html", """
      <!--suppress ProblematicWhitespace --><html>
      \t<body></body>
      </html>""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testSuppression2() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    myFixture.configureByText("x.css", """
      /*noinspection ProblematicWhitespace*/
      div {
       font-family: arial, helvetica;
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testSuppression3() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = false;
    inspectionFixture.get().doTest("""
             @SuppressWarnings("ProblematicWhitespace") class X {
             \tString s;
             }
             """);
  }
}
