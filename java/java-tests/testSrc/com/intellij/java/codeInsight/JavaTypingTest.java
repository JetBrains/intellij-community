// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AbstractBasicJavaTypingTest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@TestApplication
public class JavaTypingTest extends AbstractBasicJavaTypingTest {
  //doesn't support in general case because of resolving
  @Test
  public void testCommaAfterDefaultAnnotationArgumentWhenArrayIsExpected() {
    doTest(',');
  }

  //doesn't support because of formatting
  @Test
  public void testMulticaretIndentLBrace() {
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testMulticaretIndentRBrace() {
    doTest('}');
  }

  //doesn't support because of formatting
  @Test
  public void testDotOnNewLine() { doTest('.'); }

  @Test
  public void testDotOnNewLineWithStatementBelow() { doTest('.'); }

  @Test
  public void testDotOnNewLineAfterFirstCallWithStatementBelow() { doTest('.'); }

  @Test
  public void testDotOnNewLineAfterFirstCallWithStatementBelowAligned() {
    doTestWithCodeStyleSettings('.', s -> s.ALIGN_MULTILINE_CHAINED_METHODS = true, s -> s.ALIGN_MULTILINE_CHAINED_METHODS = false);
  }

  @Test
  public void testDotOnNewLineWithStatementBelowAligned() {
    doTestWithCodeStyleSettings('.', s -> s.ALIGN_MULTILINE_CHAINED_METHODS = true, s -> s.ALIGN_MULTILINE_CHAINED_METHODS = false);
  }

  @Test
  public void testDotOnNewLineWithStatementBelowNoLineBreak() {
    doTestWithCodeStyleSettings('.', s -> s.KEEP_LINE_BREAKS = false, s -> s.KEEP_LINE_BREAKS = true);
  }

  @Test
  public void testDotOnNewLineAfterFirstCallWithStatementBelowNoLineBreak() {
    doTestWithCodeStyleSettings('.', s -> s.KEEP_LINE_BREAKS = false, s -> s.KEEP_LINE_BREAKS = true);
  }

  @Test
  public void testDotOnNewLineAfterFirstBuilderCallWithStatementBelow() {
    doTestWithCodeStyleSettings('.', JavaTypingTest::keepBuilderMethodsIndents, JavaTypingTest::restoreBuilderMethodsIndents);
  }

  @Test
  public void testDotOnNewLineAfterSecondBuilderCallWithStatementBelow() {
    doTestWithCodeStyleSettings('.', JavaTypingTest::keepBuilderMethodsIndents, JavaTypingTest::restoreBuilderMethodsIndents);
  }

  @Test
  public void testDotOnNewLineAfterFirstBuilderCallWithStatementBelowNoLineBreak() {
    doTestWithCodeStyleSettings('.', s -> {
      keepBuilderMethodsIndents(s);
      s.KEEP_LINE_BREAKS = false;
    }, s -> {
      restoreBuilderMethodsIndents(s);
      s.KEEP_LINE_BREAKS = true;
    });
  }

  @Test
  public void testDotOnNewLineAfterSecondBuilderCallWithStatementBelowNoLineBreak() {
    doTestWithCodeStyleSettings('.', s -> {
      keepBuilderMethodsIndents(s);
      s.KEEP_LINE_BREAKS = false;
    }, s -> {
      restoreBuilderMethodsIndents(s);
      s.KEEP_LINE_BREAKS = true;
    });
  }


  //doesn't support because of formatting
  @Test
  public void testEqualAfterBitwiseOp() { doTest('='); }

  //doesn't support because of formatting
  @Test
  public void testEqualAfterBitwiseOp2() {
    doTestWithCodeStyleSettings('=', s -> s.SPACE_WITHIN_PARENTHESES = true, s -> s.SPACE_WITHIN_PARENTHESES = false);
  }

  //doesn't support because of formatting
  @Test
  public void testFixWhileByBrace() {
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testIndentRBrace() {
    doTest('}');
    doTestUndo();
  }

  //doesn't support because of formatting
  @Test
  public void testFixIfByBrace() {
    doTest('{');
  }

  //doesn't support in general case because of resolving
  @Test
  public void testQuestionAfterPolyadic() { doTest('?'); }

  //doesn't support in general case because of resolving
  @Test
  public void testQuestionAfterPolyadic2() { doTest('?'); }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRule() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRuleNewLine() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRuleNewLine2() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRule2ThrowStatement() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRule2Expression() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRule3Expression() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  //doesn't support because of formatting
  @Test
  public void testCloseBracesAfterSwitchRule3ExpressionOldLine() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  @Test
  public void testOpenBracesAfterSwitchRuleStatementInStringLiteral() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  @Test
  public void testOpenBracesAfterSwitchRuleExpressionAssignmentInStringLiteral() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  @Test
  public void testOpenBracesAfterSwitchRuleExpressionInStringLiteral() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  @Test
  public void testOpenBracesAfterSwitchRuleExpressionInTextBlockLiteral() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  @Test
  public void testOpenBracesAfterSwitchRuleExpressionInCharLiteral() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest('{');
  }

  private static void keepBuilderMethodsIndents(@NotNull CommonCodeStyleSettings settings) {
    settings.BUILDER_METHODS = "withA,withB";
    settings.KEEP_BUILDER_METHODS_INDENTS = true;
  }

  private static void restoreBuilderMethodsIndents(@NotNull CommonCodeStyleSettings settings) {
    settings.BUILDER_METHODS = "";
    settings.KEEP_BUILDER_METHODS_INDENTS = false;
  }

  private void doTestWithCodeStyleSettings(char typeChar, Consumer<CommonCodeStyleSettings> newValue, Consumer<CommonCodeStyleSettings> restore) {
    CodeInsightTestFixture myFixture = getFixture();
    myFixture.configureByFile(getTestName(true) + "_before.java");
    CommonCodeStyleSettings settings = ReadAction.computeBlocking(() -> CodeStyle.getLanguageSettings(myFixture.getFile()));
    newValue.accept(settings);
    try {
      myFixture.type(typeChar);
      myFixture.checkResultByFile(getTestName(true) + "_after.java");
    }
    finally {
      restore.accept(settings);
    }
  }
}
