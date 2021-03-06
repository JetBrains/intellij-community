// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class ExtractableExpressionPart {
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

  public boolean isEquivalent(@NotNull ExtractableExpressionPart part) {
    if (myVariable != null && myVariable.equals(part.myVariable)) {
      return true;
    }
    if (myValue != null && myValue.equals(part.myValue)) {
      return true;
    }
    PsiExpression usage1 = PsiUtil.skipParenthesizedExprDown(myUsage);
    PsiExpression usage2 = PsiUtil.skipParenthesizedExprDown(part.myUsage);
    return usage1 != null && usage2 != null && JavaPsiEquivalenceUtil.areExpressionsEquivalent(usage1, usage2);
  }

  @Nullable
  static ExtractableExpressionPart match(@NotNull PsiExpression expression,
                                         @NotNull List<? extends PsiElement> scope,
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
  static ExtractableExpressionPart matchVariable(@NotNull PsiReferenceExpression expression, @Nullable List<? extends PsiElement> scope) {
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

  @NotNull
  public static ExtractableExpressionPart fromUsage(@NotNull PsiExpression usage, @NotNull PsiType type) {
    PsiType usageType;
    //noinspection AssertWithSideEffects
    assert (usageType = usage.getType()) == null || type.isAssignableFrom(usageType)
      : "expected " + type.getCanonicalText() + ", got " + usageType.getCanonicalText();
    return new ExtractableExpressionPart(usage, null, null, type);
  }

  @Override
  public String toString() {
    return myUsage.getText();
  }
}
