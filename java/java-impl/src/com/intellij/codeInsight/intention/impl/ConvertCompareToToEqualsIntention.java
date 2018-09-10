/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ConvertCompareToToEqualsIntention extends BaseElementAtCaretIntentionAction {

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final CompareToResult compareToResult = CompareToResult.findCompareTo(element);
    assert compareToResult != null;
    final PsiExpression qualifier = compareToResult.getQualifier();
    final PsiExpression argument = compareToResult.getArgument();
    final StringBuilder text = new StringBuilder();
    if (!compareToResult.isEqEq()) {
      text.append('!');
    }
    if (qualifier != null) {
      text.append(qualifier.getText()).append('.');
    }
    text.append("equals(").append(argument.getText()).append(')');
    final PsiExpression newExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(text.toString(), null);
    final PsiElement result = compareToResult.getBinaryExpression().replace(newExpression);

    editor.getCaretModel().moveToOffset(result.getTextOffset() + result.getTextLength());
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    return CompareToResult.findCompareTo(element) != null;
  }

  private static class CompareToResult {

    private final PsiBinaryExpression myBinaryExpression;
    private final PsiMethodCallExpression myCompareToCall;

    private CompareToResult(PsiBinaryExpression binaryExpression, PsiMethodCallExpression compareToCall) {
      myBinaryExpression = binaryExpression;
      myCompareToCall = compareToCall;
    }

    public PsiBinaryExpression getBinaryExpression() {
      return myBinaryExpression;
    }

    public boolean isEqEq() {
      return JavaTokenType.EQEQ.equals(myBinaryExpression.getOperationTokenType());
    }

    public PsiExpression getArgument() {
      return myCompareToCall.getArgumentList().getExpressions()[0];
    }

    public PsiExpression getQualifier() {
      return myCompareToCall.getMethodExpression().getQualifierExpression();
    }

    @Nullable
    static CompareToResult findCompareTo(PsiElement element) {
      final PsiBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiBinaryExpression.class);
      if (binaryExpression == null) {
        return null;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.NE.equals(tokenType) && !JavaTokenType.EQEQ.equals(tokenType)) {
        return null;
      }
      PsiMethodCallExpression compareToExpression;
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      if (lhs instanceof PsiMethodCallExpression) {
        compareToExpression = (PsiMethodCallExpression)lhs;
        if (!MethodCallUtils.isCompareToCall(compareToExpression) || !ExpressionUtils.isZero(rhs)) {
          return null;
        }
      } else if (rhs instanceof PsiMethodCallExpression) {
        compareToExpression = (PsiMethodCallExpression)rhs;
        if (!ExpressionUtils.isZero(lhs) || !MethodCallUtils.isCompareToCall(compareToExpression)) {
          return null;
        }
      } else {
        return null;
      }
      return new CompareToResult(binaryExpression, compareToExpression);
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert 'compareTo()' expression to 'equals()' call";
  }

  @NotNull
  @Override
  public String getText() {
    return "Convert 'compareTo()' expression to 'equals()' call (may change semantics)";
  }
}