// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.introduceVariable.IntroduceEmptyVariableHandlerImpl;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Danila Ponomarenko
 */
public class IntroduceVariableIntentionAction extends BaseRefactoringIntentionAction {
  @NotNull
  @Override
  public String getText() {
    return JavaBundle.message("intention.introduce.variable.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof SyntheticElement){
      return false;
    }

    if (getTypeOfUnfilledParameter(editor, element) != null) return true;

    final PsiExpression expression = detectExpressionStatement(element);
    if (expression == null) {
      return false;
    }

    if (expression.getParent() instanceof PsiExpressionStatement) {
      if (!PsiUtil.isStatement(expression.getParent()) ||
          expression.getParent().getLastChild() instanceof PsiErrorElement &&
          editor.getCaretModel().getOffset() == expression.getParent().getTextRange().getEndOffset()) {
        // Same action is available as an error quick-fix
        return false;
      }
    }


    final PsiType expressionType = expression.getType();
    return expressionType != null && !PsiType.VOID.equals(expressionType) && !(expression instanceof PsiAssignmentExpression);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiType type = getTypeOfUnfilledParameter(editor, element);
    if (type != null) {
      new IntroduceEmptyVariableHandlerImpl().invoke(editor, element.getContainingFile(), type);
      return;
    }

    final PsiExpression expression = detectExpressionStatement(element);
    if (expression == null){
      return;
    }

    RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
    JavaIntroduceVariableHandlerBase handler = (JavaIntroduceVariableHandlerBase)supportProvider.getIntroduceVariableHandler();
    assert handler != null;
    handler.invoke(project, editor, expression);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  private static PsiExpression detectExpressionStatement(@NotNull PsiElement element) {
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(element);
    if (prevSibling instanceof PsiStatement) {
      return getExpression((PsiStatement)prevSibling);
    }
    else {
      while(!(element instanceof PsiExpressionStatement) && !(element instanceof PsiReturnStatement)) {
        element = element.getParent();
        if (element == null || element instanceof PsiCodeBlock) return null;
      }
      return getExpression(((PsiStatement)element));
    }
  }

  private static PsiExpression getExpression(PsiStatement statement) {
    if (statement instanceof PsiExpressionStatement) {
      return ((PsiExpressionStatement)statement).getExpression();
    }

    if (statement instanceof PsiReturnStatement) {
      return ((PsiReturnStatement)statement).getReturnValue();
    }
    return null;
  }

  @Nullable
  private static PsiType getTypeOfUnfilledParameter(@NotNull Editor editor, @NotNull PsiElement element) {
    if (element.getParent() instanceof PsiExpressionList && element.getParent().getParent() instanceof PsiMethodCallExpression) {
      PsiJavaToken leftBoundary = PsiTreeUtil.getPrevSiblingOfType(element, PsiJavaToken.class);
      PsiJavaToken rightBoundary = element instanceof PsiJavaToken ? (PsiJavaToken)element
                                                                   : PsiTreeUtil.getNextSiblingOfType(element, PsiJavaToken.class);
      if (leftBoundary != null && rightBoundary != null &&
          CharArrayUtil.isEmptyOrSpaces(editor.getDocument().getImmutableCharSequence(),
                                        leftBoundary.getTextRange().getEndOffset(),
                                        rightBoundary.getTextRange().getStartOffset())) {
        PsiMethod method = CompletionMemory.getChosenMethod((PsiCall)element.getParent().getParent());
        if (method != null) {
          List<PsiJavaToken> allTokens = PsiTreeUtil.getChildrenOfTypeAsList(element.getParent(), PsiJavaToken.class);
          PsiParameterList parameterList = method.getParameterList();
          int parameterIndex = allTokens.indexOf(leftBoundary);
          if (parameterIndex >= 0 && parameterIndex < parameterList.getParametersCount()) {
            PsiParameter parameter = parameterList.getParameters()[parameterIndex];
            return parameter.getType();
          }
        }
      }
    }
    return null;
  }
}
