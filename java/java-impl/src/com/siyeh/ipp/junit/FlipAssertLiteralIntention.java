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
package com.siyeh.ipp.junit;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class FlipAssertLiteralIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("flip.assert.literal.intention.family.name");
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String fromMethodName = methodExpression.getReferenceName();
    @NonNls final String toMethodName = getOppositeAssertMethodName(fromMethodName);
    return CommonQuickFixBundle.message("fix.replace.x.with.y", fromMethodName + "()", toMethodName + "()");
  }

  @NotNull
  private static String getOppositeAssertMethodName(String fromMethodName) {
    return "assertTrue".equals(fromMethodName) ? "assertFalse" : "assertTrue";
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new AssertTrueOrFalsePredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String fromMethodName = methodExpression.getReferenceName();
    @NonNls final String toMethodName = getOppositeAssertMethodName(fromMethodName);
    final CommentTracker tracker = new CommentTracker();
    @NonNls final StringBuilder newCall = new StringBuilder();
    final PsiElement qualifier = methodExpression.getQualifier();
    if (qualifier == null) {
      final PsiMethod method = call.resolveMethod();
      assert method != null;
      final PsiClass aClass = method.getContainingClass();
      assert aClass != null;
      final String qualifiedName = aClass.getQualifiedName();
      assert qualifiedName != null;
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !ImportUtils.addStaticImport(qualifiedName, toMethodName, element)) {
        newCall.append(qualifiedName).append('.');
      }
    }
    else {
      newCall.append(tracker.text(qualifier)).append('.');
    }
    newCall.append(toMethodName).append('(');
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();

    if (arguments.length == 1) {
      newCall.append(BoolUtils.getNegatedExpressionText(arguments[0], tracker));
    }
    else {
      newCall.append(tracker.text(arguments[0])).append(',');
      newCall.append(BoolUtils.getNegatedExpressionText(arguments[1], tracker));
    }
    newCall.append(')');
    PsiReplacementUtil.replaceExpressionAndShorten(call, newCall.toString(), tracker);
  }
}