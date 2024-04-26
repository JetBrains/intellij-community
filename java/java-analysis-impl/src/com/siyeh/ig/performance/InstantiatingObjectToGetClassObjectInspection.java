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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class InstantiatingObjectToGetClassObjectInspection
  extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "instantiating.object.to.get.class.object.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    if (ExpressionUtils.isVoidContext(methodCallExpression)) {
      return null;
    }
    return new InstantiatingObjectToGetClassObjectFix();
  }

  private static class InstantiatingObjectToGetClassObjectFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "instantiating.object.to.get.class.object.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression expression = (PsiMethodCallExpression)startElement;
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (type == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(expression, getTypeText(type, new StringBuilder()) + ".class", new CommentTracker());
    }

    private static StringBuilder getTypeText(PsiType type, StringBuilder text) {
      if (type instanceof PsiArrayType arrayType) {
        text.append("[]");
        getTypeText(arrayType.getComponentType(), text);
      }
      else if (type instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)type).resolve();
        if (aClass != null && PsiUtil.isLocalClass(aClass)) {
          text.insert(0, aClass.getName());
          aClass = aClass.getContainingClass();
          while (aClass != null) {
            text.insert(0, aClass.getName() + '.');
            aClass = aClass.getContainingClass();
          }
        }
        else {
          final String canonicalText = type.getCanonicalText();
          final String typeText = PsiNameHelper.getQualifiedClassName(canonicalText, false);
          text.insert(0, typeText);
        }
      }
      else {
        text.insert(0, type.getCanonicalText());
      }
      return text;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstantiatingObjectToGetClassObjectVisitor();
  }

  private static class InstantiatingObjectToGetClassObjectVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"getClass".equals(methodName) || !expression.getArgumentList().isEmpty()) {
        return;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
      if (!(qualifier instanceof PsiNewExpression newExpression)) {
        return;
      }
      if (newExpression.getAnonymousClass() != null) {
        return;
      }
      registerError(expression, expression);
    }
  }
}