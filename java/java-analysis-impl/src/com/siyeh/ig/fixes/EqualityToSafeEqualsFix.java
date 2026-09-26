/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EqualityToSafeEqualsFix extends PsiUpdateModCommandQuickFix implements PriorityAction {

  private final boolean myNegated;
  private final Priority myPriority;

  private EqualityToSafeEqualsFix(boolean negated, @NotNull Priority priority) {
    myNegated = negated;
    myPriority = priority;
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPriority;
  }

  public static @Nullable EqualityToSafeEqualsFix buildFix(PsiBinaryExpression expression) {
    final Nullability nullability = NullabilityUtil.getExpressionNullability(expression.getLOperand(), true);
    if (nullability == Nullability.NOT_NULL) return null;
    final Priority priority = nullability == Nullability.UNKNOWN ? Priority.TOP : Priority.NORMAL;
    return new EqualityToSafeEqualsFix(JavaTokenType.NE.equals(expression.getOperationTokenType()), priority);
  }

  @Override
  public @Nls @NotNull String getName() {
    return myNegated
           ? InspectionGadgetsBundle.message("inequality.to.safe.not.equals.quickfix")
           : InspectionGadgetsBundle.message("equality.to.safe.equals.quickfix");
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("equality.to.safe.equals.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement comparisonToken, @NotNull ModPsiUpdater updater) {
    final PsiElement parent = comparisonToken.getParent();
    if (!(parent instanceof PsiBinaryExpression expression)) {
      return;
    }
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    if (lhs == null ||  rhs == null) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final String lhsText = tracker.text(lhs);
    final String rhsText = tracker.text(rhs);
    final @NonNls StringBuilder newExpression = new StringBuilder();
    if (PsiUtil.isAvailable(JavaFeature.OBJECTS_CLASS, expression) && ClassUtils.findClass("java.util.Objects", expression) != null) {
      if (JavaTokenType.NE.equals(expression.getOperationTokenType())) {
        newExpression.append('!');
      }
      newExpression.append("java.util.Objects.equals(").append(lhsText).append(',').append(rhsText).append(')');
    }
    else {
      newExpression.append(lhsText).append("==null?").append(rhsText).append(expression.getOperationSign().getText()).append(" null:");
      if (JavaTokenType.NE.equals(expression.getOperationTokenType())) {
        newExpression.append('!');
      }
      if (ParenthesesUtils.getPrecedence(lhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
        newExpression.append('(').append(lhsText).append(')');
      }
      else {
        newExpression.append(lhsText);
      }
      newExpression.append(".equals(").append(rhsText).append(')');
    }

    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression.toString(), tracker);
  }
}