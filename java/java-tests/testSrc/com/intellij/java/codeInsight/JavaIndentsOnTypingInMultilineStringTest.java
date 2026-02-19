// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class JavaIndentsOnTypingInMultilineStringTest extends BasePlatformTestCase  {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    indentOptions.USE_TAB_CHARACTER = true;
  }

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
    myFixture.configureByText(JavaFileType.INSTANCE, before);
    myFixture.type(typing);
    myFixture.checkResult(after);
  }

}
