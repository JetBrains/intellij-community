// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.editor.actions;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actions.MoveStatementUpDownTestUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class JavaMoveStatementUpDownTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
    setPerverseCodeStyleSettings();
  }

  private void doTest() {
    MoveStatementUpDownTestUtil.doTest(codeInsightFixture.get(), testMethodName);
  }

  private static Project getProject() {
    return projectFixture.get();
  }

  private void setPerverseCodeStyleSettings() {
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.BLANK_LINES_AROUND_FIELD = 3;
    javaSettings.BLANK_LINES_AROUND_METHOD = 4;
    javaSettings.BLANK_LINES_AROUND_CLASS = 5;
    javaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
    javaSettings.KEEP_BLANK_LINES_IN_CODE = 0;
    javaSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
  }

  public static void setStandardCodeStyleSettings(Project project) {
    CodeStyleSettings settings = CodeStyle.createTestSettings();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.BLANK_LINES_AROUND_FIELD = 0;
    javaSettings.BLANK_LINES_AROUND_METHOD = 1;
    javaSettings.BLANK_LINES_AROUND_CLASS = 0;
    javaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
    javaSettings.KEEP_BLANK_LINES_IN_CODE = 2;
    javaSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
    CodeStyleSettingsManager.getInstance(project).setTemporarySettings(settings);
  }

  @Test
  public void testStatement() { doTest(); }
  @Test
  public void testBlockStatement() { doTest(); }
  @Test
  public void testSelection() { doTest(); }
  @Test
  public void testMoveOutsideMethod() { doTest(); }
  @Test
  public void testMoveOutsideClass() { doTest(); }
  @Test
  public void testMoveOutsideNestedClass() { doTest(); }
  @Test
  public void testIfStatement() { doTest(); }
  @Test
  public void testIfStatement2() { doTest(); }
  @Test
  public void testComplexCondition() { doTest(); }
  @Test
  public void testComment() { doTest(); }
  @Test
  public void testMethod() { doTest(); }
  @Test
  public void testMethodInsideClass() { doTest(); }
  @Test
  public void testDeclarations() { doTest(); }
  @Test
  public void testSurroundWithBlock() { doTest(); }
  @Test
  public void testCommentAtLineStart() { doTest(); }
  @Test
  public void testEmptyLinesBetween() { doTest(); }
  @Test
  public void testEmptyLinesBetweenFields() { doTest(); }
  @Test
  public void testEmptyLinesBetweenFields2() { doTest(); }
  @Test
  public void testMethodAnnotation() { doTest(); }
  @Test
  public void testAnonClass() { doTest(); }
  @Test
  public void testMethodReturnElement() { doTest(); }
  @Test
  public void testInsideEnum() { doTest(); }
  @Test
  public void testInsideEnum2() { doTest(); }
  @Test
  public void testMoveConstruction() { doTest(); }
  @Test
  public void testMoveStatementWithAnonClass() { doTest(); }
  @Test
  public void testMoveStatementWithBlankLine() { doTest(); }
  @Test
  public void testSomewhereInTheMiddle() { doTest(); }
  @Test
  public void testLastJavaLine() { doTest(); }
  @Test
  public void testFieldWithBlankLineAround() { setStandardCodeStyleSettings(getProject()); doTest(); }
  @Test
  public void testFieldWithBlankLineAround2() { setStandardCodeStyleSettings(getProject()); doTest(); }

  @Test
  public void testSurroundWithBlockPerversiveCodeStyle() {
    CodeStyleSettings rootSettings = CodeStyleSettingsManager.getInstance(getProject()).createTemporarySettings();
    CommonCodeStyleSettings settings = rootSettings.getCommonSettings(JavaLanguage.INSTANCE);
    settings.BLANK_LINES_AROUND_FIELD = 0;
    settings.BLANK_LINES_AROUND_METHOD = 1;
    settings.BLANK_LINES_AROUND_CLASS = 0;
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
    settings.KEEP_BLANK_LINES_IN_CODE = 2;
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    doTest();
  }

  @Test
  public void testClassMultilineModifierList() { setStandardCodeStyleSettings(getProject()); doTest(); }
  @Test
  public void testBraces() { setStandardCodeStyleSettings(getProject()); doTest(); }
  @Test
  public void testFieldWithJavadoc() { setStandardCodeStyleSettings(getProject()); doTest(); }
  @Test
  public void testClosingCurlyBrace() { doTest(); }
  @Test
  public void testAcrossAnonymous() { doTest(); }
  @Test
  public void testAcrossAnonymousStatement() { doTest(); }
  @Test
  public void testMoveMultiAnonymous() { doTest(); }
  @Test
  public void testSingleLineAnonymous() { doTest(); }
  @Test
  public void testAnonymousMethodCall() { doTest(); }
  @Test
  public void testAnonymousAssignment() { doTest(); }
  @Test
  public void testAnonymousInIfStatement() { doTest(); }
  @Test
  public void testTopLevelEnum() { doTest(); }
  @Test
  public void testTopLevelEnum2() { doTest(); }
  @Test
  public void testMoveClassIntoImportList() { doTest(); }
  @Test
  public void testEnumConstantWithJavadoc() { doTest(); }
  @Test
  public void testEnumConstantWithJavadoc2() { doTest(); }
  @Test
  public void testEnumConstant() { doTest(); }
  @Test
  public void testEnumConstantEmptyLine() { doTest(); }
  @Test
  public void testEnumConstantEmptyLine2() { doTest(); }
  @Test
  public void testEnumConstantWithBody() { doTest(); }
  @Test
  public void testEnumConstantWithBody2() { doTest(); }
  @Test
  public void testCaseBlock() { doTest(); }
  @Test
  public void testCaseBlock2() { doTest(); }
  @Test
  public void testCaseBlockSelection() { doTest(); }
  @Test
  public void testNonCaseBlockSelection() { doTest(); }
  @Test
  public void testCatchSection() { doTest(); }
  @Test
  public void testCatchSectionSelection() { doTest(); }
  @Test
  public void testCatchSectionSelection2() { doTest(); }
  @Test
  public void testNonsenseCatchSelection() { doTest(); }
  @Test
  public void testNonsenseCatchSelection2() { doTest(); }
  @Test
  public void testCatchSectionFormatting() { doTest(); }
  @Test
  public void testStatementInCatchSection() { doTest(); }
  @Test
  public void testSelectionBeyondMovedRegion() { doTest(); }
  @Test
  public void testMoveFieldThroughSpace() { setStandardCodeStyleSettings(getProject()); doTest(); }
  @Test
  public void testNotCapturingLineComment() { doTest(); }
  @Test
  public void testNotCapturingLineComments() { doTest(); }
  @Test
  public void testModuleInfo() { doTest(); }

  @Test
  public void testMoveCollapsedMethods() {
    CodeInsightTestFixture fixture = codeInsightFixture.get();
    Runnable beforeMove = () -> {
      Editor editor = fixture.getEditor();
      EditorTestUtil.buildInitialFoldingsInBackground(editor);
      editor.getFoldingModel().runBatchFoldingOperation(() -> {
        for (FoldRegion foldRegion : editor.getFoldingModel().getAllFoldRegions()) {
          foldRegion.setExpanded(false);
        }
      });
    };
    Runnable afterMove = () -> {
      Editor editor = fixture.getEditor();
      CodeFoldingManager.getInstance(getProject()).updateFoldRegions(editor);
      for (FoldRegion foldRegion : editor.getFoldingModel().getAllFoldRegions()) {
        assertFalse(foldRegion.isExpanded());
      }
    };
    MoveStatementUpDownTestUtil.doTest(fixture, testMethodName, beforeMove, afterMove);
  }
}
