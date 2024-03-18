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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SynchronizeOnThisInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean syncOnClass = ((Boolean)infos[0]).booleanValue();
    return InspectionGadgetsBundle.message(
      syncOnClass ? "synchronize.on.class.problem.descriptor" : "synchronize.on.this.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnThisVisitor();
  }

  private static class SynchronizeOnThisVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = PsiUtil.skipParenthesizedExprDown(statement.getLockExpression());
      if (lockExpression instanceof PsiThisExpression) {
        registerError(lockExpression, Boolean.FALSE);
      }
      else if (hasJavaLangClassType(lockExpression)) {
        registerError(lockExpression, Boolean.TRUE);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (!ThreadingUtils.isNotifyOrNotifyAllCall(expression) && !ThreadingUtils.isWaitCall(expression)) {
        return;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
      if (qualifier == null || (qualifier instanceof PsiThisExpression)) {
        registerMethodCallError(expression, Boolean.FALSE);
      }
      else if (hasJavaLangClassType(qualifier)) {
        registerMethodCallError(expression, Boolean.TRUE);
      }
    }

    private static boolean hasJavaLangClassType(@Nullable PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType classType)) {
        return false;
      }
      final PsiClassType javaLangClassType = TypeUtils.getType(CommonClassNames.JAVA_LANG_CLASS, expression);
      if (!javaLangClassType.isAssignableFrom(classType)) {
        return false;
      }
      final PsiType[] parameters = classType.getParameters();
      if (parameters.length == 0) {
        return true;
      }
      if (parameters.length > 1) {
        return false;
      }
      final PsiType parameterType = parameters[0];
      if (isNonPrivateClassType(parameterType)) {
        return true;
      }
      if (!(parameterType instanceof PsiCapturedWildcardType capturedWildcardType)) {
        return false;
      }
      final PsiWildcardType wildcardType = capturedWildcardType.getWildcard();
      final PsiType extendsBoundType = wildcardType.getExtendsBound();
      return isNonPrivateClassType(extendsBoundType);
    }

    private static boolean isNonPrivateClassType(PsiType type) {
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      return aClass != null && !aClass.hasModifierProperty(PsiModifier.PRIVATE);
    }
  }
}