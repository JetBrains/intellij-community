// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/joinLines")
@TestApplication
public class JoinLinesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testNormal() { doTest(); }

  @Test
  public void testStringLiteral() { doTest(); }
  @Test
  public void testStringLiteralTrim() { doTest(); }
  @Test
  public void testLiteralSCR4989() { doTest(); }

  @Test
  public void testCallChain() { doTest(); }
  @Test
  public void testCallChain2() { doTest(); }
  @Test
  public void testCallChainLineBreak() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTest(); 
  }
  @Test
  public void testCallChainWrong() { doTest(); }
  @Test
  public void testCallChainWrong2() { doTest(); }
  @Test
  public void testDeclarationAndCall() { doTest(); }
  @Test
  public void testDeclarationAndCallSelfRef() { doTest(); }
  @Test
  public void testAssignmentAndCall() { doTest(); }
  @Test
  public void testLambdaExpression() { doTest(); }
  @Test
  public void testSwitchLabeledRule() { doTest(); }

  @Test
  public void testDeclarationAndReassignmentWithCall() { doTest(); }
  @Test
  public void testAssignmentAndReassignmentWithCall() { doTest(); }

  @Test
  public void testIfChain() { doTest(); }
  @Test
  public void testIfChainIncomplete() { doTest(); }
  @Test
  public void testIfChainCorrectIndent() { doTest(); }
  @Test
  public void testIfChainPolyadic() { doTest(); }
  @Test
  public void testIfChainNoBraces() { doTest(); }
  @Test
  public void testIfChainElse() { doTest(); }
  @Test
  public void testIfChainSelection() { doTest(); }
  @Test
  public void testAtEOF() { doTest(); }

  @Test
  public void testSCR3493() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  @Test
  public void testSCR3493a() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  @Test
  public void testSCR3493b() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  @Test
  public void testSCR3493c() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  @Test
  public void testSCR3493d() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  @Test
  public void testSCR3493e() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
    }
  }
  @Test
  public void testSCR5959() {
    doTest();
  }
  @Test
  public void testSCR6299() {
    doTest();
  }

  @Test
  public void testLocalVar() { doTest(); }
  @Test
  public void testLocalVarAnnotated() { doTest(); }
  @Test
  public void testLocalVarImplicit() { doTest(); }

  @Test
  public void testSlashComment() { doTest(); }
  @Test
  public void testSlashCommentFollowedByBlockComment() { doTest(); }
  @Test
  public void testSlashCommentFollowedByBlockCommentAndCode() { doTest(); }
  @Test
  public void testDocComment() { doTest(); }
  @Test
  public void testDocCommentMarkdown() { doTest(); }

  @Test
  public void testOnEmptyLine() { doTest(); }
  @Test
  public void testCollapseClass() { doTest(); }
  @Test
  public void testSCR10386() { doTest(); }
  @Test
  public void testDeclarationWithInitializer() {doTest(); }

  @Test
  public void testUnwrapCodeBlock1() {
    CommonCodeStyleSettings settings = getJavaSettings();
    boolean use_tab_character = settings.getIndentOptions().USE_TAB_CHARACTER;
    boolean smart_tabs = settings.getIndentOptions().SMART_TABS;
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.getIndentOptions().USE_TAB_CHARACTER = true;
      settings.getIndentOptions().SMART_TABS = true;
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
      doTest();
    } finally {
      settings.getIndentOptions().USE_TAB_CHARACTER = use_tab_character;
      settings.getIndentOptions().SMART_TABS = smart_tabs;
      settings.IF_BRACE_FORCE = old;
    }
  }

  @Test
  public void testUnwrapCodeBlock2() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.getIndentOptions().USE_TAB_CHARACTER = true;
    settings.getIndentOptions().SMART_TABS = true;
    settings.SPACE_WITHIN_BRACES = true;
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest();
  }
  
  @Test
  public void testUnwrapCodeBlockIfElse() {
    getJavaSettings().SPACE_WITHIN_BRACES = true;
    doTest();
  }

  @Test
  public void testUnwrapCodeBlockIfElse2() {
    getJavaSettings().SPACE_WITHIN_BRACES = true;
    doTest();
  }

  @Test
  public void testIfBlockWithComment() { doTest(); }
  @Test
  public void testAssignmentExpression() { doTest(); }
  @Test
  public void testAssignmentExpression2() { doTest(); }
  @Test
  public void testAssignmentExpressionPrecedence() { doTest(); }
  @Test
  public void testAssignmentExpressionPrecedence2() { doTest(); }

  @Test
  public void testReformatInsertsNewlines() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.getIndentOptions().USE_TAB_CHARACTER = true;
    settings.getIndentOptions().SMART_TABS = true;
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.SPACE_WITHIN_BRACES = true;
    doTest();
  }
  
  @Test
  public void testForceBrace() {
    CommonCodeStyleSettings settings = getJavaSettings();
    int old = settings.IF_BRACE_FORCE;
    try {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      doTest();
    } finally {
      settings.IF_BRACE_FORCE = old;
    }
  }

  @Test
  public void testWrongWrapping() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.RIGHT_MARGIN = 80;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  @Test
  public void testSubsequentJoiningAndUnexpectedTextRemoval() {
    // Inspired by IDEA-65342
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.RIGHT_MARGIN = 50;
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.SPACE_WITHIN_BRACES = true;
    doTest(2);
  }
  
  @Test
  public void testLeaveTrailingComment() { doTest(); }

  @Test
  public void testConvertComment() { doTest();}
  @Test
  public void testConvertComment2() { doTest();}
  @Test
  public void testConvertFinalLineComment() { doTest();}
  @Test
  public void testConvertFinalLineComment2() { doTest();}
  @Test
  public void testConvertFinalLineComment3() { doTest();}
  @Test
  public void testConvertLongLine() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.RIGHT_MARGIN = 79;

    doTest();
  }
  @Test
  public void testConvertMultiLongLine() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.RIGHT_MARGIN = 50;

    doTest();
  }
  @Test
  public void testConvertManyEndOfLineComments() { doTest();}
  @Test
  public void testConvertMixed() { doTest();}

  @Test
  public void testJoiningMethodCallWhenItDoesntFit() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.RIGHT_MARGIN = 20;
    doTest();
  }

  @Test
  public void testMultipleBlockComments() { doTest(); }

  @Test
  public void testPreserveSpaceIfOnNewLineOptionEnabled() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.CATCH_ON_NEW_LINE = true;
    doTest();
  }

  @Test
  public void testJoinLocals1() {doTest();}
  @Test
  public void testJoinLocals2() {doTest();}
  @Test
  public void testJoinLocals3() {doTest();}
  @Test
  public void testJoinLocals4() {doTest();}
  @Test
  public void testJoinLocals5() {doTest();}
  @Test
  public void testJoinLocals6() {doTest();}
  @Test
  public void testJoinFields1() {doTest();}
  @Test
  public void testJoinFields2() {doTest(3);}
  @Test
  public void testJoinFields3() {doTest();}

  @Test
  public void testCaseLabels() {doTest();}
  @Test
  public void testCaseLabels2() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.SPACE_AFTER_COMMA = false;
    doTest();
  }
  @Test
  public void testCaseLabels3() {doTest();}
  
  @Test
  public void testJoinTextBlock() {doTest();}
  @Test
  public void testJoinTextBlockAtStartLine() {doTest();}
  @Test
  public void testJoinTextBlockAtStartLineNonEmpty() {doTest();}
  @Test
  public void testJoinTextBlockAtStartLineFinalStep() {doTest();}
  @Test
  public void testJoinTextBlockAtStartLineComplete() {doTest();}
  @Test
  public void testJoinTextBlockAtStartLineComplete2() {doTest();}
  @Test
  public void testJoinTextBlockAtStartLineCompleteWithEscapes() {doTest();}
  @Test
  public void testJoinTextBlockBackSlash() {doTest();}
  @Test
  public void testJoinTextBlockBackSlash2() {doTest();}
  @Test
  public void testJoinTextBlockBackDoubleSlash() {doTest();}
  @Test
  public void testJoinTextBlockBackTripleSlash() {doTest();}
  @Test
  public void testJoinTextBlockBackSlashEmptyLineAfter() {doTest();}
  @Test
  public void testJoinTextBlockBackSlashLastLine() {doTest();}
  @Test
  public void testJoinTextBlockSlashS() {doTest();}
  @Test
  public void testJoinTextBlockSlashSFake() {doTest();}
  @Test
  public void testJoinTextBlockSlashS2() {doTest();}
  @Test
  public void testJoinTextBlockSlashSComplete() {doTest();}
  @Test
  public void testJoinTextBlockSlashSAtTheEnd() {doTest();}
  @Test
  public void testJoinStringTemplateBackSlash() {doTest();}
  @Test
  public void testJoinStringTemplateBackSlash2() {doTest();}
  @Test
  public void testJoinOutsideOfTextBlock() { doTest(); }

  private void doTest() {
    doTest(".java");
  }
  
  private void doTest(int times) {
    doTest(".java", times);
  }

  private void doTest(@NonNls final String ext) {
    doTest(ext, 1);
  }
  
  private void doTest(@NonNls final String ext, int times) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String path = "/codeInsight/joinLines/";
    myFixture.configureByFile(path + myFixture.getTestName(false) + ext);
    myFixture.runInCommand(() -> {
      int count = times;
      while (count-- > 0) {
        performAction();
      }
      myFixture.checkResultByFile(path + myFixture.getTestName(false) + "_after" + ext);
    });
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES);
    actionHandler.execute(codeInsightFixture.get().getEditor(), null, DataManager.getInstance().getDataContext());
  }

  @NotNull
  private CommonCodeStyleSettings getJavaSettings() {
    return CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }
}
