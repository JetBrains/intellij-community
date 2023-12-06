// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaMoveStatementUpDownTest extends MoveStatementUpDownTestBase {

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setPerverseCodeStyleSettings();
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

  public void testStatement() { doTest(); }
  public void testBlockStatement() { doTest(); }
  public void testSelection() { doTest(); }
  public void testMoveOutsideMethod() { doTest(); }
  public void testMoveOutsideClass() { doTest(); }
  public void testMoveOutsideNestedClass() { doTest(); }
  public void testIfStatement() { doTest(); }
  public void testIfStatement2() { doTest(); }
  public void testComplexCondition() { doTest(); }
  public void testComment() { doTest(); }
  public void testMethod() { doTest(); }
  public void testMethodInsideClass() { doTest(); }
  public void testDeclarations() { doTest(); }
  public void testSurroundWithBlock() { doTest(); }
  public void testCommentAtLineStart() { doTest(); }
  public void testEmptyLinesBetween() { doTest(); }
  public void testEmptyLinesBetweenFields() { doTest(); }
  public void testEmptyLinesBetweenFields2() { doTest(); }
  public void testMethodAnnotation() { doTest(); }
  public void testAnonClass() { doTest(); }
  public void testMethodReturnElement() { doTest(); }
  public void testInsideEnum() { doTest(); }
  public void testInsideEnum2() { doTest(); }
  public void testMoveConstruction() { doTest(); }
  public void testMoveStatementWithAnonClass() { doTest(); }
  public void testMoveStatementWithBlankLine() { doTest(); }
  public void testSomewhereInTheMiddle() { doTest(); }
  public void testLastJavaLine() { doTest(); }
  public void testFieldWithBlankLineAround() { setStandardCodeStyleSettings(getProject()); doTest(); }
  public void testFieldWithBlankLineAround2() { setStandardCodeStyleSettings(getProject()); doTest(); }

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

  public void testClassMultilineModifierList() { setStandardCodeStyleSettings(getProject()); doTest(); }
  public void testBraces() { setStandardCodeStyleSettings(getProject()); doTest(); }
  public void testFieldWithJavadoc() { setStandardCodeStyleSettings(getProject()); doTest(); }
  public void testClosingCurlyBrace() { doTest(); }
  public void testAcrossAnonymous() { doTest(); }
  public void testAcrossAnonymousStatement() { doTest(); }
  public void testMoveMultiAnonymous() { doTest(); }
  public void testSingleLineAnonymous() { doTest(); }
  public void testAnonymousMethodCall() { doTest(); }
  public void testAnonymousAssignment() { doTest(); }
  public void testAnonymousInIfStatement() { doTest(); }
  public void testTopLevelEnum() { doTest(); }
  public void testTopLevelEnum2() { doTest(); }
  public void testMoveClassIntoImportList() { doTest(); }
  public void testEnumConstantWithJavadoc() { doTest(); }
  public void testEnumConstantWithJavadoc2() { doTest(); }
  public void testEnumConstant() { doTest(); }
  public void testEnumConstantEmptyLine() { doTest(); }
  public void testEnumConstantEmptyLine2() { doTest(); }
  public void testEnumConstantWithBody() { doTest(); }
  public void testEnumConstantWithBody2() { doTest(); }
  public void testCaseBlock() { doTest(); }
  public void testCaseBlock2() { doTest(); }
  public void testCaseBlockSelection() { doTest(); }
  public void testNonCaseBlockSelection() { doTest(); }
  public void testCatchSection() { doTest(); }
  public void testCatchSectionSelection() { doTest(); }
  public void testCatchSectionSelection2() { doTest(); }
  public void testNonsenseCatchSelection() { doTest(); }
  public void testNonsenseCatchSelection2() { doTest(); }
  public void testCatchSectionFormatting() { doTest(); }
  public void testStatementInCatchSection() { doTest(); }
  public void testSelectionBeyondMovedRegion() { doTest(); }
  public void testMoveFieldThroughSpace() { setStandardCodeStyleSettings(getProject()); doTest(); }
  public void testNotCapturingLineComment() { doTest(); }
  public void testNotCapturingLineComments() { doTest(); }
  public void testModuleInfo() { doTest(); }

  public void testMoveCollapsedMethods() {
    myBeforeMoveTask = () -> {
      EditorTestUtil.buildInitialFoldingsInBackground(getEditor());
      getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
        for (FoldRegion foldRegion : getEditor().getFoldingModel().getAllFoldRegions()) {
          foldRegion.setExpanded(false);
        }
      });
    };
    myAfterMoveTask = () -> {
      CodeFoldingManager.getInstance(getProject()).updateFoldRegions(getEditor());
      for (FoldRegion foldRegion : getEditor().getFoldingModel().getAllFoldRegions()) {
        assertFalse(foldRegion.isExpanded());
      }
    };
    doTest();
  }
}
