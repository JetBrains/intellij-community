/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InconvertibleTypesChecker;
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.Convertible;
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.TypeMismatch;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class EqualsBetweenInconvertibleTypesInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean WARN_IF_NO_MUTUAL_SUBCLASS_FOUND = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("WARN_IF_NO_MUTUAL_SUBCLASS_FOUND",
               InspectionGadgetsBundle.message("equals.between.inconvertible.types.mutual.subclass.option")));
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    final boolean convertible = (boolean)infos[2];
    if (convertible) {
      return InspectionGadgetsBundle.message(
        "equals.between.inconvertible.types.no.mutual.subclass.problem.descriptor",
        comparedType.getPresentableText(),
        comparisonType.getPresentableText());
    }
    else {
      return InspectionGadgetsBundle.message(
        "equals.between.inconvertible.types.problem.descriptor",
        comparedType.getPresentableText(),
        comparisonType.getPresentableText());
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsBetweenInconvertibleTypesVisitor();
  }

  private class EqualsBetweenInconvertibleTypesVisitor extends BaseEqualsVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ) && !tokenType.equals(JavaTokenType.NE)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (lhsType == null || rhsType == null ||
          TypeConversionUtil.isPrimitiveAndNotNull(lhsType) || TypeConversionUtil.isPrimitiveAndNotNull(rhsType) ||
          !TypeUtils.areConvertible(lhsType, rhsType) /* red code */) {
        return;
      }
      if (LambdaUtil.notInferredType(lhsType) || LambdaUtil.notInferredType(rhsType)) return;
      TypeMismatch mismatch = InconvertibleTypesChecker.deepCheck(lhsType, rhsType, getMutualSubclassMode());
      if (mismatch != null) {
        registerError(expression.getOperationSign(), mismatch);
      }
    }

    private void registerError(@NotNull PsiElement anchor, @NotNull TypeMismatch mismatch) {
      Convertible convertible = mismatch.isConvertible();
      if (convertible == Convertible.CONVERTIBLE_MUTUAL_SUBCLASS_UNKNOWN) {
        registerPossibleProblem(anchor);
      } else {
        registerError(anchor, mismatch.getLeft(), mismatch.getRight(), convertible != Convertible.NOT_CONVERTIBLE);
      }
    }

    private InconvertibleTypesChecker.LookForMutualSubclass getMutualSubclassMode() {
      if (!WARN_IF_NO_MUTUAL_SUBCLASS_FOUND) {
        return InconvertibleTypesChecker.LookForMutualSubclass.NEVER;
      }
      return isOnTheFly()
             ? InconvertibleTypesChecker.LookForMutualSubclass.IF_CHEAP
             : InconvertibleTypesChecker.LookForMutualSubclass.ALWAYS;
    }

    @Override
    public boolean checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType) {
      TypeMismatch mismatch = InconvertibleTypesChecker.checkTypes(leftType, rightType, getMutualSubclassMode());
      if (mismatch != null) {
        registerError(Objects.requireNonNull(expression.getReferenceNameElement()), mismatch);
        return true;
      }
      return false;
    }
  }
}