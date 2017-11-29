/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.introduceVariable.IntroduceEmptyVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
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
    return CodeInsightBundle.message("intention.introduce.variable.text");
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

    final PsiExpressionStatement statement = detectExpressionStatement(element);
    if (statement == null){
      return false;
    }

    final PsiExpression expression = statement.getExpression();

    final PsiType expressionType = expression.getType();
    return expressionType != null && !PsiType.VOID.equals(expressionType) && !(expression instanceof PsiAssignmentExpression);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiType type = getTypeOfUnfilledParameter(editor, element);
    if (type != null) {
      new IntroduceEmptyVariableHandler().invoke(editor, element.getContainingFile(), type);
      return;
    }

    final PsiExpressionStatement statement = detectExpressionStatement(element);
    if (statement == null){
      return;
    }

    new IntroduceVariableHandler().invoke(project, editor, statement.getExpression());
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

  private static PsiExpressionStatement detectExpressionStatement(@NotNull PsiElement element) {
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(element);
    return prevSibling instanceof PsiExpressionStatement ? (PsiExpressionStatement)prevSibling
                                                         : PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class);
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
