// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.testFramework.MapDataContext;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public class InplaceIntroduceVariableTest extends AbstractJavaInplaceIntroduceTest {
  @Nullable
  @Override
  protected PsiExpression getExpressionFromEditor() {
    SelectionModel selectionModel = getEditor().getSelectionModel();
    if (selectionModel.hasSelection()) {
      return IntroduceVariableBase.getSelectedExpression(getProject(), getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
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

  public void testFromExpression() {
     doTest(introducer -> type("expr"));
  }

  public void testFromSelection() {
     doTest(introducer -> type("a"));
  }

  public void testConflictingInnerClassName() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    doTest(introducer -> type("constants"));
  }

  public void testInsideInjectedString() {
    doTestInsideInjection(introducer -> type("expr"));
  }

  public void testInjectedString() {
    doTestInsideInjection(introducer -> {
     bringRealEditorBack();
     type("expr");
   });
  }
  
  public void testNoNameSuggested() {
    doTest(introducer -> type("xyz"));
  }

  public void testPlaceInsideLoopAndRename() {
    doTest(introducer -> type("expr"));
  }
  
  public void testPlaceInsideLambdaBody() {
    doTest(introducer -> type("expr"));
  }

  public void testPlaceInsideLambdaBodyMultipleOccurrences1() {
    doTestReplaceChoice("Replace all 0 occurrences", introducer -> type("expr"));
  }

  public void testReplaceAllOnDummyCodeWithSameNameAsGenerated() {
    doTestReplaceChoice("Replace all 0 occurrences", introducer -> type("expr"));
  }

  public void testReplaceAllIntroduceFieldInLocalClass() {
    doTestReplaceChoice("Replace all 0 occurrences", introducer -> type("smth"));
  }

  public void testReplaceAllWithScopeInvalidation() {
    doTestReplaceChoice("Replace all 0 occurrences", introducer -> type("newType"));
  }

  public void testRanges() {
     doTest(introducer -> type("expr"));
  }

  public void testFromParenthesis() {
     doTest(introducer -> type("expr"));
  }

  public void testConflictWithField() {
    doTest(introducer -> type("height"));
  }

  public void testConflictWithFieldNoCast() {
    doTest(introducer -> type("weights"));
  }

  public void testCast() {
    doTestTypeChange("Integer");
  }

  public void testCastToObject() {
    doTestTypeChange("Object");
  }

  public void testEscapePosition() {
    doTestStopEditing(introducer -> {
     invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
     invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
   });
  }

  public void testEscapePositionIfTyped() {
    doTestStopEditing(introducer -> {
     type("fooBar");
     invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
   });
  }

  public void testWritable() {
    doTestReplaceChoice("Replace read and write occurrences (will change semantics!)");
  }
  
  public void testNoWritable() {
    doTestReplaceChoice("Replace all occurrences but write");
  }
  
  public void testAllInsertFinal() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }
  
  public void testAllIncomplete() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }

  public void testStreamSimple() {
    doTestReplaceChoice("Extract as 'map' operation");
  }

  public void testStreamMultiple() {
    doTestReplaceChoice("Replace all 0 occurrences and extract as 'mapToInt' operation");
  }

  public void testStreamMultiline() {
    doTestReplaceChoice("Extract as 'map' operation");
  }

  public void testBrokenFormattingWithInValidation() {
    doTest(introducer -> type("bool"));
  }

  public void testStopEditing() {
    doTestStopEditing(introducer -> {
     invokeEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
     invokeEditorAction(IdeActions.ACTION_EDITOR_ENTER);
     invokeEditorAction(IdeActions.ACTION_EDITOR_ENTER);
   });
  }
  
  public void testInBlock1() {
    doTestReplaceChoice("Replace 0 occurrences in 'else' block");
  }
  
  public void testInBlock2() {
    doTestReplaceChoice("Replace 0 occurrences in 'if-then' block");
  }
  
  public void testInBlock3() {
    doTestReplaceChoice("Replace all 0 occurrences");
  }
  
  public void testInBlockLambda1() {
    doTestReplaceChoice("Replace 0 occurrences in 'lambda' block");
  }
  
  public void testInBlockLambda2() {
    doTestReplaceChoice("Replace 0 occurrences in outer 'lambda' block");
  }

  private void doTestStopEditing(Consumer<AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final AbstractInplaceIntroducer introducer = invokeRefactoring();
      pass.accept(introducer);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      if (state != null) {
        state.gotoEnd(true);
      }
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private void doTestTypeChange(final String newType) {
    final Consumer<AbstractInplaceIntroducer> typeChanger = introducer -> type(newType);
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final AbstractInplaceIntroducer introducer = invokeRefactoring();
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.previousTab();
      typeChanger.accept(introducer);
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private void doTestReplaceChoice(String choiceText) {
    doTestReplaceChoice(choiceText, null);
  }

  private void doTestReplaceChoice(String choiceText, Consumer<AbstractInplaceIntroducer<?, ?>> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      MyIntroduceHandler handler = createIntroduceHandler();
      UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(choiceText)));
      final AbstractInplaceIntroducer<?, ?> introducer = invokeRefactoring(handler);
      if (pass != null) {
        pass.accept(introducer);
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
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

    @Override
    protected boolean isInplaceAvailableInTestMode() {
      return true;
    }
  }
}