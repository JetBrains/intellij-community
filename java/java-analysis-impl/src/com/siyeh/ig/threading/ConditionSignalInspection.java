/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ConditionSignalInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "CallToSignalInsteadOfSignalAll";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "condition.signal.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionSignalVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ConditionSignalFix();
  }

  private static class ConditionSignalFix extends AbstractReplaceWithAnotherMethodCallFix {
    @Override
    protected @NonNls String getMethodName() {
      return "signalAll";
    }
  }

  private static class ConditionSignalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String signal = "signal";
      if (!signal.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (!argumentList.isEmpty()) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(containingClass,
                                       "java.util.concurrent.locks.Condition")) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
