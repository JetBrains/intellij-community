// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class InplaceIntroduceVariableTest extends AbstractJavaInplaceIntroduceTest {
  @Nullable
  @Override
  protected PsiExpression getExpressionFromEditor() {
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

  public void testPlaceInsideLoopAndRename() {
    doTest(introducer -> type("expr"));
  }
  
  public void testPlaceInsideLambdaBody() {
    doTest(introducer -> type("expr"));
  }

  public void testPlaceInsideLambdaBodyMultipleOccurrences1() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL, introducer -> type("expr"));
  }

  public void testReplaceAllOnDummyCodeWithSameNameAsGenerated() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL, introducer -> type("expr"));
  }

  public void testReplaceAllIntroduceFieldInLocalClass() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL, introducer -> type("smth"));
  }

  public void testReplaceAllWithScopeInvalidation() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL, introducer -> type("newType"));
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
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL);
  }
  
  public void testNoWritable() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.NO_WRITE);
  }
  
  public void testAllInsertFinal() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL);
  }
  
  public void testAllIncomplete() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.ALL);
  }

  public void testStreamSimple() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.CHAIN);
  }

  public void testStreamMultiple() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.CHAIN_ALL);
  }

  public void testStreamMultiline() {
    doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice.CHAIN);
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

  private void doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice choice) {
    doTestReplaceChoice(choice, null);
  }

  private void doTestReplaceChoice(IntroduceVariableBase.JavaReplaceChoice choice, Consumer<AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      MyIntroduceHandler handler = createIntroduceHandler();
      ((MyIntroduceVariableHandler)handler).setChoice(choice);
      final AbstractInplaceIntroducer introducer = invokeRefactoring(handler);
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

  private static void invokeEditorAction(String actionId) {
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
    private JavaReplaceChoice myChoice = null;

    public void setChoice(JavaReplaceChoice choice) {
      myChoice = choice;
    }

    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }

    @Override
    protected JavaReplaceChoice getOccurrencesChoice() {
      return myChoice;
    }

    @Override
    protected boolean isInplaceAvailableInTestMode() {
      return true;
    }
  }
}