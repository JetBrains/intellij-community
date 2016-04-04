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
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorActionTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class CompleteStatementTest extends EditorActionTestCase {
  public void testAddMissingSemicolon() throws Exception { doTest(); }
  
  public void testAddMissingSemicolonToPackageStatement() { doTest(); }

  public void testAddMissingSemicolonAfterAnonymous() { doTest(); }

  public void testAddMissingParen() throws Exception { doTest(); }

  public void testCompleteIf() throws Exception { doTest(); }

  public void testCompleteIfKeyword() throws Exception { doTest(); }

  public void testCompleteIfStatementGoesToThen() throws Exception { doTest(); }

  public void testCompleteIfKeywordStatementGoesToThen() throws Exception { doTest(); }

  public void testIndentation() throws Exception { doTest(); }

  public void testErrorNavigation() throws Exception { doTest(); }

  public void testStringLiteral() throws Exception { doTest(); }

  public void testCompleteCatch() throws Exception { doTest(); }
  
  public void testCompleteCatchLParen() throws Exception { doTest(); }

  public void testAlreadyCompleteCatch() throws Exception {
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    int old = settings.BRACE_STYLE;
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    try {
      doTest();
    }
    finally {
      settings.BRACE_STYLE = old;
    }
  }

  public void testNoBlockReformat() { doTest(); }

  public void testCompleteCatchWithExpression() throws Exception { doTest(); }

  public void testCompleteCatchBody() throws Exception { doTest(); }

  public void testSCR11147() throws Exception { doTest(); }

  public void testNoErrors() throws Exception { doTest(); }

  public void testThrow() throws Exception { doTest(); }

  public void testReturn() throws Exception { doTest(); }

  public void testEmptyLine() throws Exception { doTest(); }

  public void testBlock() throws Exception { doTest(); }

  public void testTwoStatementsInLine() throws Exception { doTest(); }

  public void testFor() throws Exception { doTest(); }

  public void testEmptyFor() { doTest(); }

  public void testForEach() throws Exception { doTest(); }

  public void testForBlock() throws Exception { doTest(); }

  public void testForIncrementExpressionAndBody() throws Exception { doTest(); }

  public void testEmptyBeforeReturn() throws Exception { doTest(); }

  public void testIf() throws Exception { doTest(); }

  public void testBeforeStatement() throws Exception { doTest(); }

  public void testTry1() throws Exception { doTest(); }

  public void testBlock1() throws Exception { doTest(); }

  public void testAfterFor() throws Exception { doTest(); }

  public void testBeforeFor() throws Exception { doTest(); }

  public void testAtBlockEnd() throws Exception { doTest(); }

  public void testForceBlock() throws Exception { doTest(); }

  public void testElseIf() throws Exception { doTest(); }

  public void testIncompleteElseIf() throws Exception { doTest(); }

  public void testField() throws Exception { doTest(); }

  public void testMethod() throws Exception { doTest(); }

  public void testClass() throws Exception { doTest(); }
  
  public void testInnerEnumBeforeMethod() { doTest(); }
  
  public void testInnerEnumBeforeMethodWithSpace() { doTest(); }

  public void testCompleteElseIf() throws Exception { doTest(); }

  public void testReformatElseIf() { doTest(); }

  public void testCompleteStringLiteral() throws Exception {
    doTest();
  }

  public void testNonAbstractMethodWithSemicolon() throws Exception { doTest(); }

  public void testReturnFromNonVoid() throws Exception { doTest(); }

  public void testReturnFromVoid() throws Exception { doTest(); }

  public void testIncompleteCall() throws Exception { doTest(); }

  public void testCompleteCall() throws Exception { doTest(); }

  public void testStartNewBlock() throws Exception { doTest(); }

  public void testInPreceedingBlanks() throws Exception { doTest(); }

  public void testNoBlockReturn() throws Exception { doTest(); }

  public void testInComment() throws Exception { doTest(); }

  public void testInComment2() throws Exception { doTest(); }
  public void testInComment3() throws Exception { doTest(); }
  public void testInComment4() throws Exception { doTest(); }

  public void testSCR22904() throws Exception { doTest(); }
  public void testSCR30227() throws Exception { doTest(); }

  public void testFieldWithInitializer() throws Exception { doTest(); }

  public void testFieldBeforeAnnotation() throws Exception { doTest(); }
  public void testMethodBeforeAnnotation() throws Exception { doTest(); }
  public void testMethodBeforeCommentField() throws Exception { doTest(); }
  public void testMethodBeforeCommentMethod() throws Exception { doTest(); }

  public void testParenthesized() throws Exception { doTest(); }

  public void testCompleteBreak() throws Exception {
    doTest();
  }

  public void testCompleteIfNextLineBraceStyle() throws Exception {
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
  }

  public void testCompleteIfNextLineBraceStyle2() throws Exception {
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
  }

  public void testSCR36110() throws Exception {
    doTest();
  }

  public void testSCR37331() throws Exception { doTest(); }
  public void testIDEADEV434() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    doTest();
    CodeStyleSettingsManager.getSettings(getProject()).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  public void testIDEADEV1093() throws Exception { doTest(); }

  public void testIDEADEV1710() throws Exception { doTest(); }

  public void testInterfaceMethodSemicolon() throws Exception { doTest(); }

  public void testSynchronized() throws Exception { doTest(); }

  public void testCdrEndlessLoop() throws Exception { doTest(); }

  public void testFollowedByComment() throws Exception { doTest(); }

  public void testBraceFixeNewLine() throws Exception { doTest(); }

  public void testSwitchKeyword() throws Exception { doTest(); }

  public void testSwitchKeywordWithCondition() throws Exception { doTest(); }
  public void testSwitchBraces() { doTest(); }
  public void testCaseColon() { doTest(); }

  public void testNewInParentheses() throws Exception { doTest(); }
  
  public void testIDEADEV20713() throws Exception { doTest(); }

  public void testIDEA22125() throws Exception { doTest(); }
  
  public void testIDEA22385() throws Exception { doTest(); }

  public void testIDEADEV40479() throws Exception { doTest(); }

  public void testMultilineReturn() throws Exception { doTest(); }
  public void testMultilineCall() throws Exception { doTest(); }

  public void testIDEADEV13019() throws Exception {
    doTestBracesNextLineStyle();
  }

  public void testIDEA25139() throws Exception {
    doTestBracesNextLineStyle();
  }

  public void testClassBracesNextLine() throws Exception {
    doTestBracesNextLineStyle();
  }

  public void testBeforeIfRBrace() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    doTest();
  }

  public void testNoUnnecessaryEmptyLineAtCodeBlock() throws Exception { doTest(); }

  public void testForStatementGeneration() throws Exception { doTest(); }

  public void testSpaceAfterSemicolon() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_AFTER_SEMICOLON = true;
    doTest();
  }

  public void testNoSpaceAfterSemicolon() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_SEMICOLON = false;
    doTest();
  }
  
  public void testForUpdateGeneration() throws Exception { doTest(); }

  public void testValidCodeBlock() throws Exception { doTest(); }

  public void testValidCodeBlockWithEmptyLineAfterIt() throws Exception { doTest(); }
  
  public void testFromJavadocParameterDescriptionEndToNextParameter() throws Exception { doTest(); }

  public void testFromJavadocParameterDescriptionMiddleToNextParameter() throws Exception { doTest(); }

  public void testLastJavadocParameterDescription() throws Exception { doTest(); }
  
  public void testLastJavadocParameterDescriptionToReturn() throws Exception { doTest(); }
  
  public void testCompleteMethodCallAtReturn() throws Exception { doTest(); }
  
  public void testGenericMethodBody() throws Exception { doTest(); }

  public void testDefaultMethodBody() { doTest(); }
  public void testStaticInterfaceMethodBody() { doTest(); }
  public void testPrivateInterfaceMethodBody() { doTest(); }

  public void testArrayInitializerRBracket() throws Exception { doTest(); }
  public void testArrayInitializerRBrace() { doTest(); }
  public void testArrayInitializerSeveralLines() { doTest(); }

  public void testReturnInLambda() { doTest(); }
  
  private void doTestBracesNextLineStyle() throws Exception {
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    try {
      doTest();
    }
    finally {
      settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
      settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
      settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    }
  }

  private void doTest() {
    doTest("java");
  }

  private void doTest(String ext) {
    String path = "/codeInsight/completeStatement/";
    doFileTest(path + getTestName(false) + "." + ext, path + getTestName(false) + "_after." + ext, true);
  }

  @Override
  protected String getActionId() {
    return IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
