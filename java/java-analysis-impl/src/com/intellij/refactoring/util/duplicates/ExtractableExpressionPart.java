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
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class ExtractableExpressionPart {
  final PsiExpression myUsage;
  final PsiVariable myVariable;
  final Object myValue;
  final PsiType myType;

  private ExtractableExpressionPart(@NotNull PsiExpression usage, PsiVariable variable, Object value, @NotNull PsiType type) {
    myUsage = usage;
    myVariable = variable;
    myValue = value;
    myType = type;
  }

  @Nullable
  static PsiType commonType(@NotNull ExtractableExpressionPart part1, @NotNull ExtractableExpressionPart part2) {
    return commonType(part1.myType, part2.myType);
  }

  @Nullable
  private static PsiType commonType(@NotNull PsiType type1, @NotNull PsiType type2) {
    if (type1.isAssignableFrom(type2)) {
      return type1;
    }
    if (type2.isAssignableFrom(type1)) {
      return type2;
    }
    return null;
  }


  boolean isEquivalent(@NotNull ExtractableExpressionPart part) {
    if (myVariable != null && myVariable.equals(part.myVariable)) {
      return true;
    }
    if (myValue != null && myValue.equals(part.myValue)) {
      return true;
    }
    return JavaPsiEquivalenceUtil.areExpressionsEquivalent(PsiUtil.skipParenthesizedExprDown(myUsage),
                                                           PsiUtil.skipParenthesizedExprDown(part.myUsage));
  }

  @Nullable
  static ExtractableExpressionPart match(@NotNull PsiExpression expression,
                                         @NotNull List<PsiElement> scope,
                                         @Nullable ParameterFolding parameterFolding) {
    if (PsiUtil.isConstantExpression(expression)) {
      if (PsiTreeUtil.findChildOfType(expression, PsiJavaCodeReferenceElement.class) != null) {
        return null;
      }
      return matchConstant(expression);
    }
    if (expression instanceof PsiReferenceExpression) {
      return matchVariable((PsiReferenceExpression)expression, scope);
    }
    if (parameterFolding != null && parameterFolding.isAcceptableComplexity(expression)) {
      PsiType type = expression.getType();
      if (type != null && !PsiType.VOID.equals(type)) {
        return new ExtractableExpressionPart(expression, null, null, type);
      }
    }
    return null;
  }

  @Nullable
  private static ExtractableExpressionPart matchConstant(@NotNull PsiExpression expression) {
    PsiConstantEvaluationHelper constantHelper = JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper();
    Object value = constantHelper.computeConstantExpression(expression, false);
    if (value != null) {
      PsiType type = expression.getType();
      if (type != null) {
        return new ExtractableExpressionPart(expression, null, value, type);
      }
    }
    return null;
  }

  @Nullable
  static ExtractableExpressionPart matchVariable(@NotNull PsiReferenceExpression expression, @Nullable List<PsiElement> scope) {
    PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiVariable && (scope == null || !DuplicatesFinder.isUnder(resolved, scope))) {
      PsiVariable variable = (PsiVariable)resolved;
      return new ExtractableExpressionPart(expression, variable, null, variable.getType());
    }
    return null;
  }

  @NotNull
  public PsiExpression getUsage() {
    return myUsage;
  }
}
