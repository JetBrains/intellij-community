/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.commutative;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class FlipCommutativeMethodCallIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("flip.commutative.method.call.intention.family.name");
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    assert methodName != null;
    if ("equals".equals(methodName) || "equalsIgnoreCase".equals(methodName)) {
      return IntentionPowerPackBundle.message(
        "flip.commutative.method.call.intention.name", methodName);
    }
    else {
      return IntentionPowerPackBundle.message(
        "flip.commutative.method.call.intention.name1", methodName);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new FlipCommutativeMethodCallPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression expression = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression argument = argumentList.getExpressions()[0];
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
    final PsiExpression strippedQualifier = PsiUtil.skipParenthesizedExprDown(qualifier);
    final PsiExpression strippedArgument = PsiUtil.skipParenthesizedExprDown(argument);
    if (strippedQualifier == null) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    tracker.grabComments(qualifier);
    tracker.markUnchanged(strippedQualifier);
    tracker.grabComments(argument);
    tracker.markUnchanged(strippedArgument);
    final PsiElement newArgument = strippedQualifier.copy();
    methodExpression.setQualifierExpression(strippedArgument);
    argument.replace(newArgument);
    tracker.insertCommentsBefore(expression);
  }
}