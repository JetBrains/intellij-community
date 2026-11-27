// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.generation.surroundWith;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurroundDescriptor;
import com.intellij.codeInsight.generation.surroundWith.JavaWithBlockSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithCastSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithDoWhileSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithForSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfElseExpressionSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfElseSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfExpressionSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithNotSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithNullCheckSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithParenthesesSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithRunnableSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithSynchronizedSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchFinallySurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryFinallySurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithWhileSurrounder;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeInsight.folding.JavaFoldingFixture;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaSurroundWithTest {
  private static final String BASE_PATH = "/codeInsight/generation/surroundWith/java/";

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @SuppressWarnings("UnusedDeclaration")
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

  @Test
  public void testCommentAsFirstSurroundStatement() {
    String template = "CommentAsFirst%sSurroundStatement";
    for (SurroundType type : SurroundType.values()) {
      doTest(String.format(template, StringUtil.capitalize(type.toFileName())), type.getSurrounder());
    }
  }
  
  @Test
  public void testSurroundCompleteLineWithIf() {
    doTest(new JavaWithIfSurrounder());
  }

  @Test
  public void testSurroundCompleteLineWithIfElse() {
    doTest(new JavaWithIfElseSurrounder());
  }
  
  @Test
  public void testSurroundCompleteLineWithTryFinally() {
    doTest(new JavaWithTryFinallySurrounder());
  }

  @Test
  public void testSurroundWithStatementWithoutSelection() {
    doTest(new JavaWithIfSurrounder());
  }

  @Test
  public void testSurroundSwitchCaseWithIf() {
    doTestNotApplicable(new JavaWithIfSurrounder());
  }

  @Test
  public void testSurroundSwitchBreakWithIf() {
    doTest(daemonFixture.get().getFixture().getTestName(false), new JavaWithIfSurrounder());
  }

  @Test
  public void testSurroundNonExpressionWithParenthesis() {
    doTest(new JavaWithParenthesesSurrounder());
  }

  @Test
  public void testSurroundNonExpressionWithCast() {
    doTest(new JavaWithCastSurrounder());
  }

  @Test
  public void testSurroundExpressionWithCastEmptyLineAfter() {
    doTestWithTemplateFinish(daemonFixture.get().getFixture().getTestName(false), new JavaWithCastSurrounder(), "var");
  }

  @Test
  public void testSurroundExpressionWithCastEmptyLineAfter_2() {
    doTestWithTemplateFinish(daemonFixture.get().getFixture().getTestName(false), new JavaWithCastSurrounder(), null);
  }

  @Test
  public void testSurroundNonExpressionWithNot() {
    doTest(new JavaWithNotSurrounder());
  }

  @Test
  public void testSurroundBinaryWithCast() {
    TemplateManagerImpl.setTemplateTesting(daemonFixture.get().getFixture().getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  @Test
  public void testSurroundConditionalWithCast() {
    TemplateManagerImpl.setTemplateTesting(daemonFixture.get().getFixture().getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  @Test
  public void testSurroundAssignmentWithCast() {
    TemplateManagerImpl.setTemplateTesting(daemonFixture.get().getFixture().getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  @Test
  public void testSurroundWithNotNullCheck() { doTest(new JavaWithNullCheckSurrounder()); }
  @Test
  public void testSurroundExpressionWithIf() { doTest(new JavaWithIfExpressionSurrounder()); }
  @Test
  public void testSurroundExpressionWithIfForBoxedBooleans() { doTest(new JavaWithIfExpressionSurrounder()); }
  @Test
  public void testSurroundExpressionWithNotForBoxedBooleans() { doTest(new JavaWithNotSurrounder()); }
  @Test
  public void testSurroundExpressionWithElseIf() { doTest(new JavaWithIfExpressionSurrounder()); }
  @Test
  public void testSurroundExpressionWithElseIfElse() { doTest(new JavaWithIfElseExpressionSurrounder()); }
  @Test
  public void testCaseBlockWithIf() { doTest(new JavaWithIfSurrounder()); }
  @Test
  public void testCaseResultWithIf() { doTest(new JavaWithIfSurrounder()); }
  @Test
  public void testCaseResultWithSynchronized() { doTest(new JavaWithSynchronizedSurrounder()); }
  @Test
  public void testCaseThrowWithBlock() { doTest(new JavaWithBlockSurrounder()); }
  @Test
  public void testCaseThrowWithIf() { doTest(new JavaWithIfSurrounder()); }
  @Test
  public void testCaseThrowWithTryCatch() { doTest(new JavaWithTryCatchSurrounder()); }
  @Test
  public void testCatchBlockWithFor() { doTest(new JavaWithForSurrounder()); }
  @Test
  public void testCatchResultWithFor() { doTest(new JavaWithForSurrounder()); }
  @Test
  public void testDefaultBlockWithDoWhile() { doTest(new JavaWithDoWhileSurrounder()); }
  @Test
  public void testDefaultBlockWithTryFinally() { doTest(new JavaWithTryFinallySurrounder()); }
  @Test
  public void testDefaultResultWithRunnable() { doTest(new JavaWithRunnableSurrounder()); }
  @Test
  public void testDefaultResultWithTryCatchFinally() { doTest(new JavaWithTryCatchFinallySurrounder()); }
  @Test
  public void testDefaultResultWithWhile() { doTest(new JavaWithWhileSurrounder()); }
  @Test
  public void testDefaultThrowWithIfElse() { doTest(new JavaWithIfElseSurrounder()); }

  @Test
  public void testSurroundWithTryFinallyUsingIndents() {
    CommonCodeStyleSettings.IndentOptions indentOptions =
      CodeStyle.getSettings(daemonFixture.get().getFixture().getProject()).getIndentOptions(JavaFileType.INSTANCE);
    indentOptions.USE_TAB_CHARACTER = true;
    doTest(new JavaWithTryFinallySurrounder());
  }

  @Test
  public void testSurroundWithTryCatchFunctionalExpression() {
    doTest(new JavaWithTryCatchSurrounder());
  }

  @Test
  public void testSurroundWithTryCatchProperties() {
    FileTemplate template = FileTemplateManager.getInstance(daemonFixture.get().getFixture().getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
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

  @Test
  public void testSurroundWithTryCatchWithFinalParameter() {
    FileTemplate template = FileTemplateManager.getInstance(daemonFixture.get().getFixture().getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_DECLARATION);
    String old = template.getText();
    template.setText("final ${EXCEPTION_TYPE} ex");
    try {
      doTest(new JavaWithTryCatchSurrounder());
    }
    finally {
      template.setText(old);
    }
  }

  @Test
  public void testSurroundIfBranchWithNoBracesAndComment() {
    doTest(new JavaWithBlockSurrounder());
  }

  @Test
  public void testNoParenthesisSurrounderForLambdaParameter() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");

    ReadAction.runBlocking(() -> {
      SurroundDescriptor item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE));
      assertNotNull(item);
      SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
      PsiElement[] elements = item.getElementsToSurround(myFixture.getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      assertThat(elements).isEmpty();
    });
  }

  private void doTest(Surrounder surrounder) {
    doTest(daemonFixture.get().getFixture().getTestName(false), surrounder);
  }

  private void doTest(@NotNull String fileName, Surrounder surrounder) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + fileName + ".java");

    ReadAction.runBlocking(() -> {
      SurroundDescriptor item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE));
      assertNotNull(item);
      SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
      PsiElement[] elements = item.getElementsToSurround(myFixture.getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      assertTrue(surrounder.isApplicable(elements));
    });

    myFixture.runInCommand(() -> SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder));

    myFixture.checkResultByFile(BASE_PATH + fileName + "_after.java");
  }

  private void doTestNotApplicable(Surrounder surrounder) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");

    ReadAction.runBlocking(() -> {
      SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
      List<SurroundDescriptor> descriptors = LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE);

      boolean atLeastOneFound = false;
      for (SurroundDescriptor item : descriptors) {
        PsiElement[] elements = item.getElementsToSurround(myFixture.getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        if (elements.length != 0) {
          assertFalse(surrounder.isApplicable(elements), "applicable " + item);
          atLeastOneFound = true;
        }
      }
      assertTrue(atLeastOneFound, "atLeastOneFound");
    });
  }

  private void doTestWithTemplateFinish(@NotNull String fileName, Surrounder surrounder, @Nullable String textToType) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    daemon.configureByFile(BASE_PATH + fileName + ".java");
    myFixture.runInCommand(() -> {
      SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);

      if (textToType != null) {
        daemon.type(textToType);
      }
      TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      assertNotNull(templateState);
      templateState.nextTab();
    });
    myFixture.checkResultByFile(BASE_PATH + fileName + "_after.java");
  }

  @Test
  public void testInvokingSurroundInOneLineFoldedMethod() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureFromFileText("a.java",
                          """
                            class Foo {
                             void bar() {
                              Sy<caret>stem.out.println();
                             }
                            }""");
    List<AnAction> actions = EdtTestUtil.runInEdtAndGet(() -> {
      JavaFoldingFixture.performInitialFolding(myFixture.getEditor());
      return SurroundWithHandler.buildSurroundActions(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    });
    assertEquals(2, ContainerUtil.findAll(actions, a -> {
      String text = a.getTemplatePresentation().getText();
      return text != null && text.contains("while");
    }).size());
  }

  @Test
  public void testExcludeVoidExpressions() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureFromFileText("a.java",
                          """
                            class Foo {
                             void bar() {
                              <selection>System.out.println()</selection>;
                             }
                            }""");
    ReadAction.runBlocking(() -> {
      SelectionModel model = myFixture.getEditor().getSelectionModel();
      PsiExpression expr =
        IntroduceVariableUtil.getSelectedExpression(myFixture.getFile().getProject(), myFixture.getFile(), model.getSelectionStart(), model.getSelectionEnd());
      assertNotNull(expr);
      assertFalse(new JavaWithParenthesesSurrounder().isApplicable(expr));
      assertFalse(new JavaWithCastSurrounder().isApplicable(expr));
    });
  }

  @Test
  public void testExcludeNonVoidStatements() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureFromFileText("a.java",
                          """
                            class Foo {
                             int bar() {return 1;}
                             {   <selection>bar();</selection>
                             }
                            }""");
    ReadAction.runBlocking(() -> {
      SelectionModel model = myFixture.getEditor().getSelectionModel();
      PsiElement[] elements =
        new JavaExpressionSurroundDescriptor().getElementsToSurround(myFixture.getFile(), model.getSelectionStart(), model.getSelectionEnd());
      assertThat(elements).isEmpty();
    });
  }

  @Test
  public void testPsiTextConsistentAfterSurround() {
    doTest(new JavaWithParenthesesSurrounder());
    ReadAction.runBlocking(() -> PsiTestUtil.checkFileStructure(daemonFixture.get().getFixture().getFile()));
  }
}
