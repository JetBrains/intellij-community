// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.refactoring.InplaceIntroduceTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractBasicCompleteStatementTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture =
    codeInsightModuleFixture(projectFixture, JAVA_MODULE_ENTITY_TYPE_ID_NAME);

  private String testName;
  private CodeStyleSettings mySettings;
  private CommonCodeStyleSettings myJavaSettings;

  private static String getActionId() {
    return IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT;
  }

  @BeforeEach
  void setUpCompleteStatementTest(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PathJavaTestUtil.getCommunityJavaTestDataPath() + "/codeInsight/completeStatement/");
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
    mySettings = CodeStyle.getSettings(projectFixture.get());
    myJavaSettings = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
  }

  @Test
  public void testAddMissingSemicolon() { doTest(); }

  @Test
  public void testAddMissingSemicolonToPackageStatement() { doTest(); }

  @Test
  public void testAddMissingSemicolonAfterAnonymous() { doTest(); }

  @Test
  public void testAddMissingParen() { doTest(); }

  @Test
  public void testCompleteIf() { doTest(); }

  @Test
  public void testCompleteIfKeyword() { doTest(); }

  @Test
  public void testCompleteIfStatementGoesToThen() { doTest(); }

  @Test
  public void testAddBracesToIfAndElse() { doTest(); }

  @Test
  public void testAddBracesToIfThenOneLiner() { doTest(); }

  @Test
  public void testCompleteIfKeywordStatementGoesToThen() { doTest(); }

  @Test
  public void testIndentation() { doTest(); }

  @Test
  public void testErrorNavigation() { doTest(); }

  @Test
  public void testStringLiteral() { doTest(); }

  @Test
  public void testCompleteCatch() { doTest(); }

  @Test
  public void testCompleteCatchLParen() { doTest(); }

  @Test
  public void testAlreadyCompleteCatch() {
    myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  @Test
  public void testNoBlockReformat() { doTest(); }

  @Test
  public void testCompleteCatchWithExpression() { doTest(); }

  @Test
  public void testCompleteCatchBody() { doTest(); }

  @Test
  public void testSCR11147() { doTest(); }

  @Test
  public void testNoErrors() { doTest(); }

  @Test
  public void testThrow() { doTest(); }
  
  @Test
  
  public void testThrowTwoLinesSemicolon() { doTest(); }

  @Test
  public void testReturn() { doTest(); }

  @Test
  public void testEmptyLine() { doTest(); }

  @Test
  public void testBlock() { doTest(); }

  @Test
  public void testTwoStatementsInLine() { doTest(); }

  @Test
  public void testFor() { doTest(); }

  @Test
  public void testEmptyFor() { doTest(); }

  @Test
  public void testForEach() { doTest(); }

  @Test
  public void testForBlock() { doTest(); }

  @Test
  public void testForIncrementExpressionAndBody() { doTest(); }

  @Test
  public void testEmptyBeforeReturn() { doTest(); }

  @Test
  public void testIf() { doTest(); }

  @Test
  public void testIfWithComment() { doTest(); }

  @Test
  public void testIfWithoutParentheses() { doTest(); }

  @Test
  public void testBeforeStatement() { doTest(); }

  @Test
  public void testTry1() { doTest(); }

  @Test
  public void testInsideResourceVariable() { doTest(); }

  @Test
  public void testBlock1() { doTest(); }

  @Test
  public void testAfterFor() { doTest(); }

  @Test
  public void testBeforeFor() { doTest(); }

  @Test
  public void testForSingleStatementInBody() { doTest(); }

  @Test
  public void testForEachSingleStatementInBody() { doTest(); }

  @Test
  public void testForEachNextStatementNotIndented() { doTest(); }

  @Test
  public void testAtBlockEnd() { doTest(); }

  @Test
  public void testForceBlock() { doTest(); }

  @Test
  public void testElseIf() { doTest(); }

  @Test
  public void testBlockBeforeElseIf() { doTest(); }

  @Test
  public void testIncompleteElseIf() { doTest(); }

  @Test
  public void testField() { doTest(); }

  @Test
  public void testMethod() { doTest(); }

  @Test
  public void testLikelyField() { doTest(); }

  @Test
  public void testVoidMethodIncomplete() { doTest(); }

  @Test
  public void testFieldWithEquals() { doTest(); }

  @Test
  public void testClass() { doTest(); }

  @Test
  public void testInnerEnumBeforeMethod() { doTest(); }

  @Test
  public void testInnerEnumBeforeMethodWithSpace() { doTest(); }

  @Test
  public void testCompleteElseIf() { doTest(); }

  @Test
  public void testReformatElseIf() { doTest(); }

  @Test
  public void testCompleteStringLiteral() { doTest(); }

  @Test
  public void testNonAbstractMethodWithSemicolon() { doTest(); }

  @Test
  public void testReturnFromNonVoid() { doTest(); }

  @Test
  public void testReturnFromVoid() { doTest(); }

  @Test
  public void testIncompleteCall() { doTest(); }

  @Test
  public void testCompleteCall() { doTest(); }

  @Test
  public void testStartNewBlock() { doTest(); }

  @Test
  public void testInPrecedingBlanks() { doTest(); }

  @Test
  public void testNoBlockReturn() { doTest(); }

  @Test
  public void testInComment() { doTest(); }

  @Test
  public void testInComment2() { doTest(); }

  @Test
  public void testInComment3() { doTest(); }

  @Test
  public void testInComment4() { doTest(); }

  @Test
  public void testSCR22904() { doTest(); }

  @Test
  public void testSCR30227() { doTest(); }

  @Test
  public void testFieldWithInitializer() { doTest(); }

  @Test
  public void testFieldBeforeAnnotation() { doTest(); }

  @Test
  public void testMethodBeforeAnnotation() { doTest(); }

  @Test
  public void testMethodBeforeCommentField() { doTest(); }

  @Test
  public void testMethodBeforeCommentMethod() { doTest(); }

  @Test
  public void testCloseAnnotationWithArrayInitializer() { doTest(); }

  @Test
  public void testParenthesized() { doTest(); }

  @Test
  public void testCompleteBreak() { doTest(); }
  
  @Test
  
  public void testNakedWhile() { doTest(); }

  @Test
  public void testCompleteIfNextLineBraceStyle() {
    myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  @Test
  public void testCompleteIfNextLineBraceStyle2() {
    myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  @Test
  public void testSCR36110() { doTest(); }

  @Test
  public void testSCR37331() { doTest(); }

  @Test
  public void testGenericBeforeVar() { doTest(); }

  @Test
  public void testIDEADEV434() {
    mySettings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    doTest();
    mySettings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  @Test
  public void testIDEADEV1093() { doTest(); }

  @Test
  public void testIDEADEV1710() { doTest(); }

  @Test
  public void testInterfaceMethodSemicolon() { doTest(); }

  @Test
  public void testSynchronized() { doTest(); }

  @Test
  public void testCdrEndlessLoop() { doTest(); }

  @Test
  public void testFollowedByComment() { doTest(); }

  @Test
  public void testBraceFixNewLine() { doTest(); }

  @Test
  public void testSwitchKeyword() { doTest(); }

  @Test
  public void testSwitchKeywordWithCondition() { doTest(); }

  @Test
  public void testSwitchBraces() { doTest(); }

  @Test
  public void testCaseColon() { doTest(); }

  @Test
  public void testMultipleCasesColon() { doTest(); }

  @Test
  public void testDefaultColon() { doTest(); }

  @Test
  public void testNewInParentheses() { doTest(); }

  @Test
  public void testIDEADEV20713() { doTest(); }

  @Test
  public void testIDEA22125() { doTest(); }

  @Test
  public void testIDEA22385() { doTest(); }

  @Test
  public void testIDEADEV40479() { doTest(); }

  @Test
  public void testMultilineReturn() { doTest(); }

  @Test
  public void testMultilineCall() { doTest(); }

  @Test
  public void testVarargCall() { doTest(); }

  @Test
  public void testOverloadedCall() { doTest(); }

  @Test
  public void testIDEADEV13019() { doTestBracesNextLineStyle(); }

  @Test
  public void testIDEA25139() { doTestBracesNextLineStyle(); }

  @Test
  public void testClassBracesNextLine() { doTestBracesNextLineStyle(); }

  @Test
  public void testBeforeIfRBrace() {
    mySettings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    doTest();
  }

  @Test
  public void testNoUnnecessaryEmptyLineAtCodeBlock() { doTest(); }

  @Test
  public void testForStatementGeneration() { doTest(); }

  @Test
  public void testSpaceAfterSemicolon() {
    mySettings.getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_SEMICOLON = true;
    doTest();
  }

  @Test
  public void testNoSpaceAfterSemicolon() {
    myJavaSettings.SPACE_AFTER_SEMICOLON = false;
    doTest();
  }

  @Test
  public void testForUpdateGeneration() { doTest(); }

  @Test
  public void testReformatForHeader() { doTest(); }

  @Test
  public void testValidCodeBlock() { doTest(); }

  @Test
  public void testValidCodeBlockWithEmptyLineAfterIt() { doTest(); }

  @Test
  public void testFromJavadocParameterDescriptionEndToNextParameter() { doTest(); }

  @Test
  public void testFromJavadocParameterDescriptionMiddleToNextParameter() { doTest(); }

  @Test
  public void testLastJavadocParameterDescription() { doTest(); }

  @Test
  public void testLastJavadocParameterDescriptionToReturn() { doTest(); }

  @Test
  public void testCompleteMethodCallAtReturn() { doTest(); }

  @Test
  public void testGenericMethodBody() { doTest(); }

  @Test
  public void testDefaultMethodBody() { doTest(); }

  @Test
  public void testStaticInterfaceMethodBody() { doTest(); }

  @Test
  public void testPrivateInterfaceMethodBody() { doTest(); }

  @Test
  public void testAddMethodBodyFromInsideAnnotation() { doTest(); }

  @Test
  public void testArrayInitializerRBracket() { doTest(); }

  @Test
  public void testArrayInitializerRBrace() { doTest(); }

  @Test
  public void testArrayInitializerRBrace2() { doTest(); }

  @Test
  public void testMultiArrayInitializerRBrace() { doTest(); }

  @Test
  public void testMultiArrayInitializerRBrace2() { doTest(); }

  @Test
  public void testArrayInitializerSeveralLines() { doTest(); }

  @Test
  public void testReturnInLambda() { doTest(); }

  @Test
  public void testSemicolonAfterLambda() { doTest(); }

  @Test
  public void testModuleInfo() { doTest(); }

  @Test
  public void testDoubleFieldDeclaration() { doTest(); }

  @Test
  public void testAddTernaryColon() { doTest(); }

  @Test
  public void testRecord() { doTest(); }

  @Test
  public void testRecordWithComponent() { doTest(); }

  @Test
  public void testRecordWithComponentNoBody() { doTest(); }

  @Test
  public void testClassBeforeRecord() { doTest(); }

  @Test
  public void testVarargMethod() { doTest(); }

  @Test
  public void testVarargMethod2() { doTest(); }

  @Test
  public void testVarargMethod3() { doTest(); }

  @Test
  public void testSemicolonAfterSwitchExpression() { doTest(); }

  @Test
  public void testOverloadedMethod() { doTest(); }

  @Test
  public void testOverloadedMethodNoCaretHint() { doTest(); }

  @Test
  public void testOverloadedMethodOneOrThree() { doTest(); }
  @Test
  public void testOverloadedMethodOneOrThree2() { doTest(); }

  @Test
  public void testOverloadedMethodOneOrThree3() { doTest(); }

  @Test
  public void testMissingComma() { doTest(); }

  @Test
  public void testInInjection() { doTest(); }

  @Test
  public void testNativeMethod() {
    doTest();
  }

  @Test
  public void testNativePrivateMethod() {
    doTest();
  }

  @Test
  public void testReturnSwitchExpression() {
    doTest();
  }

  @Test
  public void testReturnSwitchExpression2() {
    doTest();
  }

  @Test
  public void testReturnSwitchExpression3() {
    doTest();
  }

  @Test
  public void testSwitchAtTheEndOfClass() { doTest(); }

  @Test
  public void testAddMissingLambdaBody() { doTest(); }

  @Test
  public void testAddMissingLambdaBody2() { doTest(); }

  @Test
  public void testBlockInSwitchRule() { doTest(); }

  @Test
  public void testSwitchAddArrow() { doTest(); }

  @Test
  public void testYieldSemicolon() { doTest(); }
  @Test
  public void testCommentSmartEnter() { doTest(false); }

  @Test
  public void testWhileStatementWithCondition() { doTest(); }

  private void doTestBracesNextLineStyle() {
    myJavaSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    myJavaSettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    myJavaSettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean ignoreTrailingSpaces) {
    doFileTest(testName + ".java", testName + "_after.java", ignoreTrailingSpaces);
  }

  protected void doFileTest(String filePathBefore, String filePathAfter, boolean ignoreTrailingSpaces) {
    CodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByFile(filePathBefore);
    InplaceIntroduceTestUtil.runInCommand(fixture.getProject(), () -> {
      invokeAction();
      fixture.checkResultByFile(filePathAfter, ignoreTrailingSpaces);
    });
  }

  private static void invokeAction() {
    final String actionId = getActionId();
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    //noinspection HardCodedStringLiteral
    assertNotNull(action, "Can find registered action with id=" + actionId);
    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext()));
  }
}