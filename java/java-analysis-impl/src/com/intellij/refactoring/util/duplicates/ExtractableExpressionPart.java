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
import com.intellij.psi.tree.IElementType;
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

  @NotNull
  ExtractableExpressionPart copy() {
    return new ExtractableExpressionPart(myUsage, myVariable, myValue, myType);
  }

  @NotNull
  ExtractableExpressionPart deepCopy() {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myUsage.getProject());
    PsiExpression usageCopy = factory.createExpressionFromText(myUsage.getText(), myUsage);
    return new ExtractableExpressionPart(usageCopy, myVariable, myValue, myType);
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
                                         @Nullable ComplexityHolder complexityHolder) {
    if (expression instanceof PsiReferenceExpression) {
      return matchVariable((PsiReferenceExpression)expression, scope);
    }
    boolean isConstant = PsiUtil.isConstantExpression(expression);
    if (isConstant) {
      // Avoid replacement of coincidentally equal expressions containing different constant fields
      // E.g. don't count as equal values expressions like (Foo.A + 1) and (Bar.B - 2)
      if (PsiTreeUtil.findChildOfType(expression, PsiJavaCodeReferenceElement.class) == null) {
        return matchConstant(expression);
      }
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiExpressionStatement ||
        parent instanceof PsiExpressionList && parent.getParent() instanceof PsiExpressionListStatement) {
      return null;
    }
    if (complexityHolder != null && (isConstant || complexityHolder.isAcceptableExpression(expression))) {
      return matchExpression(expression);
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
    if (resolved instanceof PsiField && isModification(expression)) {
      return null;
    }
    if (resolved instanceof PsiVariable && (scope == null || !DuplicatesFinder.isUnder(resolved, scope))) {
      PsiVariable variable = (PsiVariable)resolved;
      return new ExtractableExpressionPart(expression, variable, null, variable.getType());
    }
    return null;
  }

  private static boolean isModification(@NotNull PsiReferenceExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (PsiTreeUtil.isAncestor(assignment.getLExpression(), expression, false)) {
        return true;
      }
    }
    else if (parent instanceof PsiUnaryExpression) {
      PsiUnaryExpression unary = (PsiUnaryExpression)parent;
      IElementType tokenType = unary.getOperationTokenType();
      if ((tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS)) &&
          PsiTreeUtil.isAncestor(unary.getOperand(), expression, false)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static ExtractableExpressionPart matchExpression(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    if (type != null && !PsiType.VOID.equals(type)) {
      return new ExtractableExpressionPart(expression, null, null, type);
    }
    return null;
  }

  @NotNull
  public PsiExpression getUsage() {
    return myUsage;
  }
}
