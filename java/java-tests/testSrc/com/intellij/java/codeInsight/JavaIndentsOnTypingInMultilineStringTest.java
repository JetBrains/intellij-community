// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightProjectFixture;

@TestApplication
public class JavaIndentsOnTypingInMultilineStringTest {
  private final TestFixture<Project> projectFixture = codeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    indentOptions.USE_TAB_CHARACTER = true;
  }

  @Test
  public void testEmptyString() {
    doTest(
      """
        class X {
          String s = \"""<caret>
                  \"""
        }
        """,
      "\n",
      """
        class X {
          String s = \"""
                  <caret>
                  \"""
        }
        """
    );
  }

  @Test
  public void testNewLineAfterFirstBracesSpacesOnly() {
    doTest(
      """
        class X {
          String s = \"""<caret>
                  foo
                  \"""
        }
        """,
      "\n",
      """
        class X {
          String s = \"""
                  <caret>
                  foo
                  \"""
        }
        """
    );
  }

  @Test
  public void testNewLineAfterFirstBracesTabsOnly() {
    doTest(
      """
        class X {
        String s = \"""<caret>
        \t\t\t\tfoo
                \"""
        }
        """,
      "\n",
      """
        class X {
        String s = \"""
        \t\t\t\t<caret>
        \t\t\t\tfoo
                \"""
        }
        """
    );
  }

  @Test
  public void testNewLineAfterFirstBracesMixed() {
    doTest(
      """
        class X {
        String s = \"""<caret>
        \t\t\t \t  foo
                \"""
        }
        """,
      "\n",
      """
        class X {
        String s = \"""
        \t\t\t \t  <caret>
        \t\t\t \t  foo
                \"""
        }
        """
    );
  }

  @Test
  public void testNewLineAfterFirstBracesMultipleNewLines() {
    doTest(
      """
        class X {
        String s = \"""<caret>
        
            \t\t\t
        \t  \t \t
        \t\t\t \t  foo
                \"""
        }
        """,
      "\n",
      """
        class X {
        String s = \"""
        \t\t\t \t  <caret>
        
            \t\t\t
        \t  \t \t
        \t\t\t \t  foo
                \"""
        }
        """
    );
  }

  private void doTest(String before, String typing, String after) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, before);
    myFixture.type(typing);
    myFixture.checkResult(after);
  }

}
