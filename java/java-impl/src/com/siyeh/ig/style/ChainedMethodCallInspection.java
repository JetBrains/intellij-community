/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ChainedMethodCallInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean m_ignoreFieldInitializations = true;

  @SuppressWarnings({"PublicField", "unused"})
  public boolean m_ignoreThisSuperCalls = true; // keep for compatibility

  @SuppressWarnings("PublicField")
  public boolean ignoreSelfTypes = true;

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignoreSelfTypes");
    writeBooleanOption(node, "ignoreSelfTypes", true);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreFieldInitializations", InspectionGadgetsBundle.message("chained.method.call.ignore.option")),
      checkbox("ignoreSelfTypes", InspectionGadgetsBundle.message("chained.method.call.ignore.self.types.option")));
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(true);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("chained.method.call.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ChainedMethodCallVisitor();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private class ChainedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression reference = expression.getMethodExpression();
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression());
      if (qualifier == null) {
        return;
      }
      if (!(qualifier instanceof PsiCallExpression)) {
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class);
      if (expressionList != null) {
        final PsiElement parent = expressionList.getParent();
        if (JavaPsiConstructorUtil.isConstructorCall(parent)) {
          return;
        }
      }
      if (ignoreSelfTypes) {
        if (qualifier instanceof PsiMethodCallExpression methodCallExpression) {
          final PsiMethod qualifierMethod = methodCallExpression.resolveMethod();
          if (qualifierMethod == null) {
            return;
          }
          PsiClass containingClass = qualifierMethod.getContainingClass();
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(qualifierMethod.getReturnType());
          if (containingClass == null || containingClass.equals(aClass)) {
            return;
          }
        }
        PsiClass callClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
        PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (qualifierClass == null || qualifierClass.equals(callClass)) {
          return;
        }
      }
      registerMethodCallError(expression);
    }
  }
}