/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public class BooleanParameterInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportMultiple = false;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    final int booleanParameterCount = ((Integer)infos[1]).intValue();
    if (booleanParameterCount == 1) {
      return method.isConstructor()
             ? InspectionGadgetsBundle.message("boolean.parameter.constructor.problem.descriptor")
             : InspectionGadgetsBundle.message("boolean.parameter.problem.descriptor");
    }
    else {
      return method.isConstructor()
             ? InspectionGadgetsBundle.message("boolean.parameters.constructor.problem.descriptor")
             : InspectionGadgetsBundle.message("boolean.parameters.problem.descriptor");
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyReportMultiple", InspectionGadgetsBundle.message("boolean.parameter.only.report.multiple.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanParameterVisitor();
  }

  private class BooleanParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null || !aClass.isInterface()) {
          return;
        }
      }
      if (PropertyUtilBase.isSimplePropertySetter(method) || LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      int count = 0;
      for (PsiParameter parameter : parameters) {
        final PsiType type = parameter.getType();
        if (!PsiTypes.booleanType().equals(type)) {
          continue;
        }
        if (MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, true)) {
          continue;
        }
        count++;
        if (count > 1) {
          break;
        }
      }

      if (count == 0 || onlyReportMultiple && count == 1) {
        return;
      }
      registerMethodError(method, method, Integer.valueOf(count));
    }
  }
}

