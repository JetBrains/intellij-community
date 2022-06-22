// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorActionTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/testData")
public class CompleteStatementTest extends EditorActionTestCase {
  private CodeStyleSettings mySettings;
  private CommonCodeStyleSettings myJavaSettings;

  @Override
  protected String getActionId() {
    return IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completeStatement/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySettings = CodeStyle.getSettings(getProject());
    myJavaSettings = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testAddMissingSemicolon() { doTest(); }
  public void testAddMissingSemicolonToPackageStatement() { doTest(); }
  public void testAddMissingSemicolonAfterAnonymous() { doTest(); }
  public void testAddMissingParen() { doTest(); }
  public void testCompleteIf() { doTest(); }
  public void testCompleteIfKeyword() { doTest(); }
  public void testCompleteIfStatementGoesToThen() { doTest(); }
  public void testAddBracesToIfAndElse() { doTest(); }
  public void testAddBracesToIfThenOneLiner() { doTest(); }
  public void testCompleteIfKeywordStatementGoesToThen() { doTest(); }
  public void testIndentation() { doTest(); }
  public void testErrorNavigation() { doTest(); }
  public void testStringLiteral() { doTest(); }
  public void testCompleteCatch() { doTest(); }
  public void testCompleteCatchLParen() { doTest(); }
  public void testAlreadyCompleteCatch() { myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE; doTest(); }
  public void testNoBlockReformat() { doTest(); }
  public void testCompleteCatchWithExpression() { doTest(); }
  public void testCompleteCatchBody() { doTest(); }
  public void testSCR11147() { doTest(); }
  public void testNoErrors() { doTest(); }
  public void testThrow() { doTest(); }
  public void testReturn() { doTest(); }
  public void testEmptyLine() { doTest(); }
  public void testBlock() { doTest(); }
  public void testTwoStatementsInLine() { doTest(); }
  public void testFor() { doTest(); }
  public void testEmptyFor() { doTest(); }
  public void testForEach() { doTest(); }
  public void testForBlock() { doTest(); }
  public void testForIncrementExpressionAndBody() { doTest(); }
  public void testEmptyBeforeReturn() { doTest(); }
  public void testIf() { doTest(); }
  public void testIfWithoutParentheses() { doTest(); }
  public void testBeforeStatement() { doTest(); }
  public void testTry1() { doTest(); }
  public void testInsideResourceVariable() { doTest(); }
  public void testBlock1() { doTest(); }
  public void testAfterFor() { doTest(); }
  public void testBeforeFor() { doTest(); }
  public void testAtBlockEnd() { doTest(); }
  public void testForceBlock() { doTest(); }
  public void testElseIf() { doTest(); }
  public void testBlockBeforeElseIf() { doTest(); }
  public void testIncompleteElseIf() { doTest(); }
  public void testField() { doTest(); }
  public void testMethod() { doTest(); }
  public void testClass() { doTest(); }
  public void testInnerEnumBeforeMethod() { doTest(); }
  public void testInnerEnumBeforeMethodWithSpace() { doTest(); }
  public void testCompleteElseIf() { doTest(); }
  public void testReformatElseIf() { doTest(); }
  public void testCompleteStringLiteral() { doTest(); }
  public void testNonAbstractMethodWithSemicolon() { doTest(); }
  public void testReturnFromNonVoid() { doTest(); }
  public void testReturnFromVoid() { doTest(); }
  public void testIncompleteCall() { doTest(); }
  public void testCompleteCall() { doTest(); }
  public void testStartNewBlock() { doTest(); }
  public void testInPrecedingBlanks() { doTest(); }
  public void testNoBlockReturn() { doTest(); }
  public void testInComment() { doTest(); }
  public void testInComment2() { doTest(); }
  public void testInComment3() { doTest(); }
  public void testInComment4() { doTest(); }
  public void testSCR22904() { doTest(); }
  public void testSCR30227() { doTest(); }
  public void testFieldWithInitializer() { doTest(); }
  public void testFieldBeforeAnnotation() { doTest(); }
  public void testMethodBeforeAnnotation() { doTest(); }
  public void testMethodBeforeCommentField() { doTest(); }
  public void testMethodBeforeCommentMethod() { doTest(); }
  public void testCloseAnnotationWithArrayInitializer() { doTest(); }
  public void testParenthesized() { doTest(); }
  public void testCompleteBreak() { doTest(); }
  public void testCompleteIfNextLineBraceStyle() { myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE; doTest(); }
  public void testCompleteIfNextLineBraceStyle2() { myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE; doTest(); }
  public void testSCR36110() { doTest(); }
  public void testSCR37331() { doTest(); }

  public void testIDEADEV434() {
    mySettings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    doTest();
    mySettings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  public void testIDEADEV1093() { doTest(); }
  public void testIDEADEV1710() { doTest(); }
  public void testInterfaceMethodSemicolon() { doTest(); }
  public void testSynchronized() { doTest(); }
  public void testCdrEndlessLoop() { doTest(); }
  public void testFollowedByComment() { doTest(); }
  public void testBraceFixNewLine() { doTest(); }
  public void testSwitchKeyword() { doTest(); }
  public void testSwitchKeywordWithCondition() { doTest(); }
  public void testSwitchBraces() { doTest(); }
  public void testCaseColon() { doTest(); }
  public void testDefaultColon() { doTest(); }
  public void testNewInParentheses() { doTest(); }
  public void testIDEADEV20713() { doTest(); }
  public void testIDEA22125() { doTest(); }
  public void testIDEA22385() { doTest(); }
  public void testIDEADEV40479() { doTest(); }
  public void testMultilineReturn() { doTest(); }
  public void testMultilineCall() { doTest(); }
  public void testVarargCall() { doTest(); }
  public void testOverloadedCall() { doTest(); }
  public void testIDEADEV13019() { doTestBracesNextLineStyle(); }
  public void testIDEA25139() { doTestBracesNextLineStyle(); }
  public void testClassBracesNextLine() { doTestBracesNextLineStyle(); }
  public void testBeforeIfRBrace() { mySettings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true; doTest(); }
  public void testNoUnnecessaryEmptyLineAtCodeBlock() { doTest(); }
  public void testForStatementGeneration() { doTest(); }
  public void testSpaceAfterSemicolon() { mySettings.getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_SEMICOLON = true; doTest(); }
  public void testNoSpaceAfterSemicolon() { myJavaSettings.SPACE_AFTER_SEMICOLON = false; doTest(); }
  public void testForUpdateGeneration() { doTest(); }
  public void testReformatForHeader() { doTest(); }
  public void testValidCodeBlock() { doTest(); }
  public void testValidCodeBlockWithEmptyLineAfterIt() { doTest(); }
  public void testFromJavadocParameterDescriptionEndToNextParameter() { doTest(); }
  public void testFromJavadocParameterDescriptionMiddleToNextParameter() { doTest(); }
  public void testLastJavadocParameterDescription() { doTest(); }
  public void testLastJavadocParameterDescriptionToReturn() { doTest(); }
  public void testCompleteMethodCallAtReturn() { doTest(); }
  public void testGenericMethodBody() { doTest(); }
  public void testDefaultMethodBody() { doTest(); }
  public void testStaticInterfaceMethodBody() { doTest(); }
  public void testPrivateInterfaceMethodBody() { doTest(); }
  public void testAddMethodBodyFromInsideAnnotation() { doTest(); }
  public void testArrayInitializerRBracket() { doTest(); }
  public void testArrayInitializerRBrace() { doTest(); }
  public void testArrayInitializerRBrace2() { doTest(); }
  public void testMultiArrayInitializerRBrace() { doTest(); }
  public void testMultiArrayInitializerRBrace2() { doTest(); }
  public void testArrayInitializerSeveralLines() { doTest(); }
  public void testReturnInLambda() { doTest(); }
  public void testSemicolonAfterLambda() { doTest(); }
  public void testModuleInfo() { doTest(); }
  public void testDoubleFieldDeclaration() { doTest(); }
  public void testAddTernaryColon() { doTest(); }
  public void testRecord() { doTest(); }
  public void testRecordWithComponent() { doTest(); }
  public void testRecordWithComponentNoBody() { doTest(); }
  public void testClassBeforeRecord() { doTest(); }
  public void testVarargMethod() { doTest(); }
  public void testVarargMethod2() { doTest(); }
  public void testVarargMethod3() { doTest(); }
  public void testSemicolonAfterSwitchExpression() { doTest(); }
  public void testOverloadedMethod() { doTest(); }
  public void testOverloadedMethodNoCaretHint() { doTest(); }
  public void testOverloadedMethodOneOrThree() { doTest(); }
  public void testOverloadedMethodOneOrThree2() { doTest(); }
  public void testOverloadedMethodOneOrThree3() { doTest(); }
  public void testMissingComma() { doTest(); }
  public void testInInjection() { doTest(); }
  public void testNativeMethod() {
    doTest();
  }
  public void testNativePrivateMethod() {
    doTest();
  }
  public void testReturnSwitchExpression() {
    doTest();
  }
  public void testReturnSwitchExpression2() {
    doTest();
  }
  public void testReturnSwitchExpression3() {
    doTest();
  }
  public void testSwitchAtTheEndOfClass() { doTest(); }

  private void doTestBracesNextLineStyle() {
    myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    myJavaSettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    myJavaSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  private void doTest() {
    String name = getTestName(false);
    doFileTest(name + ".java", name + "_after.java", true);
  }
}