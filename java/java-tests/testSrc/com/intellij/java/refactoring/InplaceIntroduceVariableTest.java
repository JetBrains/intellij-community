// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.java.JavaBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.InplaceIntroduceTestUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class InplaceIntroduceVariableTest extends AbstractJavaInplaceIntroduceTest {
  @Override
  protected boolean isJava21() {
    return true;
  }

  @Override
  protected @Nullable PsiExpression getExpressionFromEditor() {
    SelectionModel selectionModel = getEditor().getSelectionModel();
    if (selectionModel.hasSelection()) {
      return IntroduceVariableUtil.getSelectedExpression(getProject(), getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    }
    final PsiExpression expression = super.getExpressionFromEditor();
    if (expression != null) {
      return expression;
    }
    final PsiExpression expr = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expr == null && InjectedLanguageManager.getInstance(getProject()).isInjectedFragment(getFile())) {
      PsiElement element = getFile();
      return PsiTreeUtil.getParentOfType(InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element)
                                           .findElementAt(InjectedLanguageUtil.getTopLevelEditor(getEditor()).getCaretModel().getOffset()), PsiExpression.class);
    }
    return expr instanceof PsiLiteralExpression ? expr : null;
  }

  @Test
  public void testFromExpression() {
     doTest(_ -> type("expr"));
  }

  @Test
  public void testFromSelection() {
     doTest(_ -> type("a"));
  }

  @Test
  public void testConflictingInnerClassName() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    doTest(_ -> type("constants"));
  }

  @Test
  public void testUnresolvedVariable() {
    JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
    Boolean oldValue = instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE;
    instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
    try {
      doTest(_ -> type("o"));
    }
    finally {
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = oldValue;
    }

  }

  @Test
  public void testInsideInjectedString() {
    doTestInsideInjection(_ -> type("expr"));
  }

  @Test
  public void testInjectedString() {
    doTestInsideInjection(_ -> {
     bringRealEditorBack();
     type("expr");
   });
  }

  @Test
  public void testNoNameSuggested() {
    doTest(_ -> type("xyz"));
  }

  @Test
  public void testPlaceInsideLoopAndRename() {
    doTestReplaceChoice("Runnable: () -> {...}", _ -> type("expr"));
  }
  
  @Test
  public void testPlaceOutsideLoopAndRename() {
    doTestReplaceChoice(JavaBundle.message("target.code.block.presentable.text"), _ -> type("expr"));
  }

  @Test
  public void testPlaceOutsideLambdaInIfWithoutBraces() {
    doTestReplaceChoice(JavaBundle.message("target.code.block.presentable.text"), _ -> type("expr"));
  }

  @Test
  public void testPlaceOutsideLambdaInClass() {
    doTestReplaceChoice(JavaBundle.message("target.code.block.presentable.text"), _ -> type("expr"));
  }

  @Test
  public void testPlaceInsideLambdaBody() {
    doTestReplaceChoice("Runnable: () -> {...}", _ -> type("expr"));
  }

  @Test
  public void testPlaceInsideLambdaBody1() {
    doTestReplaceChoice("Predicate<String>: s -> {...}", _ -> type("first"));
  }

  @Test
  public void testPlaceInsideLambdaBodyMultipleOccurrences1() {
    doTestReplaceChoice("Replace all 0 occurrences", "Runnable: () -> {...}", _ -> type("expr"), null);
  }

  @Test
  public void testReplaceAllOnDummyCodeWithSameNameAsGenerated() {
    doTestReplaceChoice("Replace all 0 occurrences", _ -> type("expr"));
  }

  @Test
  public void testReplaceAllIntroduceFieldInLocalClass() {
    doTestReplaceChoice("Replace all 0 occurrences", _ -> type("smth"));
  }

  @Test
  public void testReplaceAllWithScopeInvalidation() {
    doTestReplaceChoice("Replace all 0 occurrences", _ -> type("newType"));
  }

  @Test
  public void testRanges() {
     doTest(_ -> type("expr"));
  }

  @Test
  public void testFromParenthesis() {
     doTest(_ -> type("expr"));
  }

  @Test
  public void testConflictWithField() {
    doTest(_ -> type("height"));
  }

  @Test
  public void testConflictWithFieldNoCast() {
    doTest(_ -> type("weights"));
  }

  @Test
  public void testCast() {
    doTestTypeChange("Integer");
  }

  @Test
  public void testCastToObject() {
    doTestTypeChange("Object");
  }

  @Test
  public void testEscapePosition() {
    doTestStopEditing(_ -> {
     invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
     invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
   });
  }

  @Test
  public void testEscapePositionIfTyped() {
    doTestStopEditing(_ -> {
     type("fooBar");
     invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
   });
  }

  @Test
  public void testRestoreEditorState() {
    doTestStopEditing(_ -> {
      invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    });
  }

  @Test
  public void testWritable() {
    doTestReplaceChoice("Replace read and write occurrences (will change semantics!)");
  }

  @Test
  public void testNoWritable() {
    doTestReplaceChoice("Replace all occurrences but write");
  }

  @Test
  public void testAllInsertFinal() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testAllIncomplete() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testStreamSimple() {
    doTestReplaceChoice("Extract as 'map' operation");
  }

  @Test
  public void testStreamMultiple() {
    doTestReplaceChoice("Replace all 0 occurrences and extract as 'mapToInt' operation");
  }

  @Test
  public void testStreamMultiline() {
    doTestReplaceChoice("Extract as 'map' operation");
  }

  @Test
  public void testDuplicateInsideLiteral() { doTestReplaceChoice("Replace all 0 occurrences"); }

  @Test
  public void testBrokenFormattingWithInValidation() {
    doTest(_ -> type("bool"));
  }

  @Test
  public void testStopEditing() {
    doTestStopEditing(_ -> {
     invokeEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
     invokeEditorAction(IdeActions.ACTION_EDITOR_ENTER);
     invokeEditorAction(IdeActions.ACTION_EDITOR_ENTER);
   });
  }

  @Test
  public void testInBlock1() {
    doTestReplaceChoice("Replace 0 occurrences in 'else' block");
  }

  @Test
  public void testSubExpression() {
    doTestReplaceChoice("Replace 0 occurrences in 'if' block");
  }

  @Test
  public void testInBlock2() {
    doTestReplaceChoice("Replace 0 occurrences in 'if-then' block");
  }

  @Test
  public void testInBlock3() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testInBlockLambda1() {
    doTestReplaceChoice("Replace 0 occurrences in 'lambda' block");
  }

  @Test
  public void testInBlockLambda2() {
    doTestReplaceChoice("Replace 0 occurrences in outer 'lambda' block");
  }

  @Test
  public void testAllLValues() {
    doTestReplaceChoice("Replace all 2 occurrences (will change semantics!)", null, null,
                        List.of("Replace this occurrence only", "Replace all 2 occurrences (will change semantics!)"));
  }

  @Test
  public void testSelectLValueThenFilterIt() {
    doTestReplaceChoice("Replace read and write occurrences (will change semantics!)", null, null,
                        List.of("Replace this occurrence only", "Replace read and write occurrences (will change semantics!)"));
  }

  @Test
  public void testSelectLValueThenFilterItFinal() {
    doTest(null);
  }

  @Test
  public void testSuperExpression() {
    try {
      doTest(null);
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "Selected expression cannot be extracted", e.getMessage());
    }
  }

  @Test
  public void testHeavilyBrokenFile() {
    doTest(null);
  }

  @Test
  public void testHeavilyBrokenFile2() {
    try {
      doTest(null);
      fail("Refactoring should not be performed");
    } catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" + JavaRefactoringBundle.message("introduce.variable.message.cannot.extract.in.implicit.class"), e.getMessage());
    }
  }

  @Test
  public void testHeavilyBrokenFile3() {
    doTest(null);
  }

  @Test
  public void testHeavilyBrokenFile4() {
    doTest(null);
  }

  @Test
  public void testHeavilyBrokenFile5() {
    doTest(null);
  }

  @Test
  public void testHeavilyBrokenFile7() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testHeavilyBrokenFile8() {
    doTest(null);
  }

  @Test
  public void testHeavilyBrokenFile9() {
    doTest(null);
  }

  @Test
  public void testHeavilyBrokenFile10() {
    doTestReplaceChoice("Replace all 0 occurrences", _ -> type("xyz"));
  }

  @Test
  public void testHeavilyBrokenFile11() {
    assertThatThrownBy(() -> doTest(null)).isInstanceOf(CommonRefactoringUtil.RefactoringErrorHintException.class)
      .hasMessageContaining("Selected block should represent an expression");
  }

  @Test
  public void testHeavilyBrokenFile12() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testTernaryInstanceOfVar() {
    // Java 17 suggests pattern replacement which is undesired in this test
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, () -> doTest(null));
  }

  @Test
  public void testAnnotationArgument() {
    assertThatThrownBy(() -> doTest(null)).isInstanceOf(CommonRefactoringUtil.RefactoringErrorHintException.class)
      .hasMessageContaining("Introduce Variable refactoring is not supported in the current context");
  }

  @Test
  public void testNullTypeAddCast() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testLambdaParameterAddCast() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  @Test
  public void testWhileTrue() {
    doTest(null);
  }

  @Test
  public void testWhilePolyadicWholeCondition() {
    doTest(null);
  }

  @Test
  public void testWhilePolyadicWholeConditionParens() {
    doTest(null);
  }

  @Test
  public void testPatternUsedInSubsequentCondition() {
    doTest(null);
  }

  @Test
  public void testNoExternalTypeAnnotations() {
    doTest(null);
  }

  @Test
  public void testNoIllegalArgumentException() {
    doTest(null);
  }

  @Test
  public void testVarWithCapture() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
      JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
      try {
        doTest(null);
      }
      finally {
        instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
      }
    });
  }
  
  @Test
  public void testVarWithCapture2() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
      JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
      try {
        doTest(null);
      }
      finally {
        instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
      }
    });
  }

  @Test
  public void testSplitMutuallyExclusiveIf() { doTestReplaceChoice("Replace all 0 occurrences"); }

  @Test
  public void testDontSplitNotMutuallyExclusiveIf() { doTestReplaceChoice("Replace all 0 occurrences"); }

  private void doTestStopEditing(Consumer<? super AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    InplaceIntroduceTestUtil.withInplaceRename(getFixture(), () -> {
      try {
        InplaceIntroduceTestUtil.runInCommand(getProject(), () -> {
          final AbstractInplaceIntroducer introducer = invokeRefactoring();
          pass.accept(introducer);
        });
        checkResultByFile(getBasePath() + name + "_after" + getExtension());
      }
      finally {
        InplaceIntroduceTestUtil.runInCommand(getProject(), () -> {
          TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
          if (state != null) {
            state.gotoEnd(true);
          }
        });
      }
    });
  }

  private void doTestTypeChange(String newType) {
    final Consumer<AbstractInplaceIntroducer> typeChanger = _ -> type(newType);
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    InplaceIntroduceTestUtil.withInplaceRename(getFixture(), () -> {
      InplaceIntroduceTestUtil.runInCommand(getProject(), () -> {
        final AbstractInplaceIntroducer introducer = invokeRefactoring();
        TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
        assert state != null;
        state.previousTab();
        typeChanger.accept(introducer);
        state.gotoEnd(false);
      });
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    });
  }

  private void doTestReplaceChoice(String choiceText) {
    doTestReplaceChoice(choiceText, null);
  }

  private void doTestReplaceChoice(String choiceText, Consumer<AbstractInplaceIntroducer<?, ?>> pass) {
    doTestReplaceChoice(choiceText, null, pass, null);
  }


  private void doTestReplaceChoice(String choiceText,
                                   String secondChoiceText,
                                   Consumer<AbstractInplaceIntroducer<?, ?>> pass,
                                   @Nullable List<String> expectedOptions) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    InplaceIntroduceTestUtil.withInplaceRename(getFixture(), () -> {
      InplaceIntroduceTestUtil.runInCommand(getProject(), () -> {
        MyIntroduceHandler handler = createIntroduceHandler();
        UiInterceptors.register(new ChooserInterceptor(expectedOptions, Pattern.quote(choiceText)));
        if (secondChoiceText != null) {
          UiInterceptors.register(new ChooserInterceptor(expectedOptions, Pattern.quote(secondChoiceText)));
        }
        final AbstractInplaceIntroducer<?, ?> introducer = invokeRefactoring(handler);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        if (pass != null) {
          pass.accept(introducer);
        }
        TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
        assert state != null;
        state.gotoEnd(false);
      });
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    });
  }

  private void invokeEditorAction(String actionId) {
    EditorActionManager.getInstance().getActionHandler(actionId)
      .execute(getEditor(), getEditor().getCaretModel().getCurrentCaret(), new MapDataContext());
  }


  @Override
  protected String getBasePath() {
    return "/refactoring/inplaceIntroduceVariable/";
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceVariableHandler();
  }

  public static class MyIntroduceVariableHandler extends IntroduceVariableHandler implements MyIntroduceHandler {
    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}