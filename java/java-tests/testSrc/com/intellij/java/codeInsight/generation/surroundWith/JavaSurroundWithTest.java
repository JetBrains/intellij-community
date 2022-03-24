// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeInsight.folding.JavaFoldingTestCase;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Denis Zhdanov
 */
public class JavaSurroundWithTest extends LightJavaCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/generation/surroundWith/java/";

  @SuppressWarnings({"UnusedDeclaration"})
  private enum SurroundType {
    IF(new JavaWithIfSurrounder()),
    IF_ELSE(new JavaWithIfElseSurrounder()),

    WHILE(new JavaWithWhileSurrounder()),
    DO_WHILE(new JavaWithDoWhileSurrounder()),
    FOR(new JavaWithForSurrounder()),

    TRY_CATCH(new JavaWithTryCatchSurrounder()),
    TRY_FINALLY(new JavaWithTryFinallySurrounder()),
    TRY_CATCH_FINALLY(new JavaWithTryCatchFinallySurrounder()),

    SYNCHRONIZED(new JavaWithSynchronizedSurrounder()),
    RUNNABLE(new JavaWithRunnableSurrounder()),
    CODE_BLOCK(new JavaWithBlockSurrounder());

    private final Surrounder mySurrounder;

    SurroundType(Surrounder surrounder) {
      mySurrounder = surrounder;
    }

    public Surrounder getSurrounder() {
      return mySurrounder;
    }

    public String toFileName() {
      StringBuilder result = new StringBuilder();
      boolean capitalize = true;
      for (char c : toString().toCharArray()) {
        if (c == '_') {
          capitalize = true;
          continue;
        }
        if (capitalize) {
          result.append(Character.toUpperCase(c));
          capitalize = false;
        }
        else {
          result.append(Character.toLowerCase(c));
        }
      }
      return result.toString();
    }
  }

  public void testCommentAsFirstSurroundStatement() {
    String template = "CommentAsFirst%sSurroundStatement";
    for (SurroundType type : SurroundType.values()) {
      doTest(String.format(template, StringUtil.capitalize(type.toFileName())), type.getSurrounder());
    }
  }

  public void testSurroundWithStatementWithoutSelection() {
    doTest(new JavaWithIfSurrounder());
  }

  public void testSurroundSwitchCaseWithIf() {
    doTestNotApplicable(new JavaWithIfSurrounder());
  }

  public void testSurroundSwitchBreakWithIf() {
    doTest(getTestName(false), new JavaWithIfSurrounder());
  }

  public void testSurroundNonExpressionWithParenthesis() {
    doTest(new JavaWithParenthesesSurrounder());
  }

  public void testSurroundNonExpressionWithCast() {
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundExpressionWithCastEmptyLineAfter() {
    doTestWithTemplateFinish(getTestName(false), new JavaWithCastSurrounder(), "var");
  }

  public void testSurroundExpressionWithCastEmptyLineAfter_2() {
    doTestWithTemplateFinish(getTestName(false), new JavaWithCastSurrounder(), null);
  }

  public void testSurroundNonExpressionWithNot() {
    doTest(new JavaWithNotSurrounder());
  }

  public void testSurroundBinaryWithCast() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundConditionalWithCast() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundAssignmentWithCast() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundWithNotNullCheck() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithNullCheckSurrounder());
  }

  public void testSurroundExpressionWithIf() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithIfExpressionSurrounder());
  }

  public void testSurroundExpressionWithIfForBoxedBooleans() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithIfExpressionSurrounder());
  }

  public void testSurroundExpressionWithNotForBoxedBooleans() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithNotSurrounder());
  }

  public void testSurroundExpressionWithElseIf() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithIfExpressionSurrounder());
  }

  public void testSurroundExpressionWithElseIfElse() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doTest(new JavaWithIfElseExpressionSurrounder());
  }

  public void testSurroundWithTryFinallyUsingIndents() {
    CommonCodeStyleSettings.IndentOptions indentOptions = getCurrentCodeStyleSettings().getIndentOptions(JavaFileType.INSTANCE);
    boolean oldUseTabs = indentOptions.USE_TAB_CHARACTER;
    indentOptions.USE_TAB_CHARACTER = true;
    doTest(new JavaWithTryFinallySurrounder());
  }

  public void testSurroundWithTryCatchFunctionalExpression() {
    doTest(new JavaWithTryCatchSurrounder());
  }

  public void testSurroundWithTryCatchProperties() {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    String old = template.getText();
    template.setText("// ${DS} \n" +
                     "${EXCEPTION}.printStackTrace();");
    try {
      doTest(new JavaWithTryCatchSurrounder());
    }
    finally {
      template.setText(old);
    }
  }

  public void testSurroundWithTryCatchWithFinalParameter() {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_DECLARATION);
    String old = template.getText();
    template.setText("final ${EXCEPTION_TYPE} ex");
    try {
      doTest(new JavaWithTryCatchSurrounder());
    }
    finally {
      template.setText(old);
    }
  }

  public void testSurroundIfBranchWithNoBracesAndComment() {
    doTest(new JavaWithBlockSurrounder());
  }

  public void testNoParenthesisSurrounderForLambdaParameter() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    SurroundDescriptor item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE));
    assertNotNull(item);
    SelectionModel selectionModel = getEditor().getSelectionModel();
    PsiElement[] elements = item.getElementsToSurround(getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    assertEmpty(elements);
  }

  private void doTest(Surrounder surrounder) {
    doTest(getTestName(false), surrounder);
  }

  private void doTest(@NotNull String fileName, Surrounder surrounder) {
    configureByFile(BASE_PATH + fileName + ".java");

    SurroundDescriptor item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE));
    assertNotNull(item);
    SelectionModel selectionModel = getEditor().getSelectionModel();
    PsiElement[] elements = item.getElementsToSurround(getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    assertTrue(surrounder.isApplicable(elements));

    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder);

    checkResultByFile(BASE_PATH + fileName + "_after.java");
  }

  private void doTestNotApplicable(Surrounder surrounder) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    SelectionModel selectionModel = getEditor().getSelectionModel();
    List<SurroundDescriptor> descriptors = LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE);

    boolean atLeastOneFound = false;
    for (SurroundDescriptor item : descriptors) {
      PsiElement[] elements = item.getElementsToSurround(getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      if (elements.length != 0) {
        assertFalse("applicable " + item, surrounder.isApplicable(elements));
        atLeastOneFound = true;
      }
    }
    assertTrue("atLeastOneFound", atLeastOneFound);
  }

  private void doTestWithTemplateFinish(@NotNull String fileName, Surrounder surrounder, @Nullable String textToType) {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    configureByFile(BASE_PATH + fileName + ".java");
    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder);

    if (textToType != null) {
      type(textToType);
    }
    TemplateState templateState = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(templateState);
    templateState.nextTab();
    checkResultByFile(BASE_PATH + fileName + "_after.java");
  }

  public void testInvokingSurroundInOneLineFoldedMethod() {
    configureFromFileText("a.java",
                          "class Foo {\n" +
                          " void bar() {\n" +
                          "  Sy<caret>stem.out.println();\n" +
                          " }\n" +
                          "}");
    JavaFoldingTestCase.performInitialFolding(getEditor());
    List<AnAction> actions = SurroundWithHandler.buildSurroundActions(getProject(), getEditor(), getFile(), null);
    assertSize(2, ContainerUtil.findAll(actions, a -> {
      String text = a.getTemplatePresentation().getText();
      return text != null && text.contains("while");
    }));
  }

  public void testExcludeVoidExpressions() {
    configureFromFileText("a.java",
                          "class Foo {\n" +
                          " void bar() {\n" +
                          "  <selection>System.out.println()</selection>;\n" +
                          " }\n" +
                          "}");
    SelectionModel model = getEditor().getSelectionModel();
    PsiExpression expr =
      IntroduceVariableUtil.getSelectedExpression(getFile().getProject(), getFile(), model.getSelectionStart(), model.getSelectionEnd());
    assertNotNull(expr);
    assertFalse(new JavaWithParenthesesSurrounder().isApplicable(expr));
    assertFalse(new JavaWithCastSurrounder().isApplicable(expr));
  }

  public void testExcludeNonVoidStatements() {
    configureFromFileText("a.java",
                          "class Foo {\n" +
                          " int bar() {return 1;}\n" +
                          " {" +
                          "   <selection>bar();</selection>\n" +
                          " }\n" +
                          "}");
    SelectionModel model = getEditor().getSelectionModel();
    PsiElement[] elements =
      new JavaExpressionSurroundDescriptor().getElementsToSurround(getFile(), model.getSelectionStart(), model.getSelectionEnd());
    assertEmpty(elements);
  }

  public void testPsiTextConsistentAfterSurround() {
    doTest(new JavaWithParenthesesSurrounder());
    PsiTestUtil.checkFileStructure(getFile());
  }
}
