// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AbstractBasicJavaTypingTest;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class JavaTypingTest extends AbstractBasicJavaTypingTest {
  //doesn't support in general case because of resolving
  public void testCommaAfterDefaultAnnotationArgumentWhenArrayIsExpected() {
    doTest(',');
  }

  //doesn't support because of formatting
  public void testMulticaretIndentLBrace() {
    doTest('{');
  }

  //doesn't support because of formatting
  public void testMulticaretIndentRBrace() {
    doTest('}');
  }

  //doesn't support because of formatting
  public void testDotOnNewLine() { doTest('.'); }

  //doesn't support because of formatting
  public void testEqualAfterBitwiseOp() { doTest('='); }

  //doesn't support because of formatting
  public void testEqualAfterBitwiseOp2() {
    myFixture.configureByFile(getTestName(true) + "_before.java");
    CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(myFixture.getFile());
    settings.SPACE_WITHIN_PARENTHESES = true;
    try {
      myFixture.type('=');
      myFixture.checkResultByFile(getTestName(true) + "_after.java");
    }
    finally {
      settings.SPACE_WITHIN_PARENTHESES = false;
    }
  }

  //doesn't support because of formatting
  public void testFixWhileByBrace() {
    doTest('{');
  }

  //doesn't support because of formatting
  public void testIndentRBrace() {
    doTest('}');
    doTestUndo();
  }

  //doesn't support because of formatting
  public void testFixIfByBrace() {
    doTest('{');
  }

  //doesn't support in general case because of resolving
  public void testQuestionAfterPolyadic() { doTest('?'); }

  //doesn't support in general case because of resolving
  public void testQuestionAfterPolyadic2() { doTest('?'); }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRule() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRuleNewLine() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRuleNewLine2() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRule2ThrowStatement() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRule2Expression() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRule3Expression() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  public void testCloseBracesAfterSwitchRule3ExpressionOldLine() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }
}
