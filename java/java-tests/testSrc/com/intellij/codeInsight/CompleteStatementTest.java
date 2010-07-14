package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.EditorActionTestCase;
import com.intellij.testFramework.TestDataPath;

/**
 * @author max
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class CompleteStatementTest extends EditorActionTestCase {
  public void testAddMissingSemicolon() throws Exception { doTest(); }

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

  public void testCompleteElseIf() throws Exception { doTest(); }

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

  public void testParenthesized() throws Exception { doTest(); }

  public void testCompleteBreak() throws Exception {
    doTest();
  }

  public void testCompleteIfNextLineBraceStyle() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.BRACE_STYLE = CodeStyleSettings.NEXT_LINE;
    doTest();
    settings.BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
  }

  public void testCompleteIfNextLineBraceStyle2() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.BRACE_STYLE = CodeStyleSettings.NEXT_LINE;
    doTest();
    settings.BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
  }

  public void testSCR36110() throws Exception {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    LanguageLevel old = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
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

  public void testNewInParentheses() throws Exception { doTest(); }
  
  public void testIDEADEV20713() throws Exception { doTest(); }

  public void testIDEA22125() throws Exception { doTest(); }
  
  public void testIDEA22385() throws Exception { doTest(); }

  public void testIDEADEV40479() throws Exception { doTest(); }

  public void testIDEADEV13019() throws Exception { 
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.BRACE_STYLE = CodeStyleSettings.NEXT_LINE;
    settings.METHOD_BRACE_STYLE = CodeStyleSettings.NEXT_LINE;
    settings.CLASS_BRACE_STYLE = CodeStyleSettings.NEXT_LINE;
    try {
      doTest();
    }
    finally {
      settings.BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
      settings.METHOD_BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
      settings.CLASS_BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
    }
  }

  private void doTest() throws Exception {
    doTest("java");
  }

  private void doTest(String ext) throws Exception {
    String path = "/codeInsight/completeStatement/";
    doFileTest(path + getTestName(false) + "." + ext, path + getTestName(false) + "_after." + ext, true);
  }

  protected String getActionId() {
    return IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
