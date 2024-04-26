/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.portability;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SystemExitInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInMainMethod = false;

  @Override
  @NotNull
  public String getID() {
    return "CallToSystemExit";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String className = (String)infos[0];
    return InspectionGadgetsBundle.message("system.exit.call.problem.descriptor", className);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInMainMethod", InspectionGadgetsBundle.message("system.exit.call.ignore.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SystemExitVisitor();
  }

  private class SystemExitVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"exit".equals(methodName) && !"halt".equals(methodName)) {
        return;
      }
      if (ignoreInMainMethod) {
        final PsiMethod containingMethod =
          PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
        if (containingMethod != null &&
            HardcodedMethodConstants.MAIN.equals(containingMethod.getName()) &&
            PsiMethodUtil.isMainMethod(containingMethod)) {
          return;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType parameterType = parameters[0].getType();
      if (!parameterType.equals(PsiTypes.intType())) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_SYSTEM.equals(className)) {
        registerMethodCallError(expression, "System");
      }
      else if ("java.lang.Runtime".equals(className)) {
        registerMethodCallError(expression, "Runtime");
      }
    }
  }
}