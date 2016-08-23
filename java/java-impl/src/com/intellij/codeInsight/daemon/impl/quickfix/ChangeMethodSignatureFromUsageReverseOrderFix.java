/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.RefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 2/9/12
 */
public class ChangeMethodSignatureFromUsageReverseOrderFix extends ChangeMethodSignatureFromUsageFix {
  public ChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                       @NotNull PsiExpression[] expressions,
                                                       @NotNull PsiSubstitutor substitutor,
                                                       @NotNull PsiElement context,
                                                       boolean changeAllUsages,
                                                       int minUsagesNumberToShowDialog) {
    super(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myTargetMethod.isValid() && myExpressions.length > myTargetMethod.getParameterList().getParametersCount()) {
      if (super.isAvailable(project, editor, file)) {
        final ArrayList<ParameterInfoImpl> result = new ArrayList<>();
        if (super.findNewParamsPlace(myExpressions, myTargetMethod, mySubstitutor,
                                     new StringBuilder(), new HashSet<>(), myTargetMethod.getParameterList().getParameters(), result)) {

          if (myNewParametersInfo.length != result.size()) return true;
          for (int i = 0, size = result.size(); i < size; i++) {
            ParameterInfoImpl info = result.get(i);
            info.setName(myNewParametersInfo[i].getName());
            if (!myNewParametersInfo[i].equals(info)) return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  protected boolean findNewParamsPlace(PsiExpression[] expressions,
                                       PsiMethod targetMethod,
                                       PsiSubstitutor substitutor,
                                       StringBuilder buf,
                                       HashSet<ParameterInfoImpl> newParams,
                                       PsiParameter[] parameters,
                                       List<ParameterInfoImpl> result) {
    // find which parameters to introduce and where
    Set<String> existingNames = new HashSet<>();
    for (PsiParameter parameter : parameters) {
      existingNames.add(parameter.getName());
    }
    int ei = expressions.length - 1;
    int pi = parameters.length - 1;
    final PsiParameter varargParam = targetMethod.isVarArgs() ? parameters[parameters.length - 1] : null;
    final List<String> params = new ArrayList<>();
    while (ei >= 0 || pi >= 0) {
      PsiExpression expression = ei >=0 ? expressions[ei] : null;
      PsiParameter parameter = pi >= 0 ? parameters[pi] : null;
      PsiType paramType = parameter == null ? null : substitutor.substitute(parameter.getType());
      boolean parameterAssignable = paramType != null && (expression == null || TypeConversionUtil
        .areTypesAssignmentCompatible(paramType, expression));
      if (parameterAssignable) {
        final PsiType type = parameter.getType();
        result.add(0, new ParameterInfoImpl(pi, parameter.getName(), type));
        params.add(0, escapePresentableType(type));
        pi--;
        ei--;
      }
      else if (isArgumentInVarargPosition(expressions, ei, varargParam, substitutor)) {
        if (pi == parameters.length - 1) {
          assert varargParam != null;
          final PsiType type = varargParam.getType();
          result.add(0, new ParameterInfoImpl(pi, varargParam.getName(), type));
          params.add(0, escapePresentableType(type));
        }
        pi--;
        ei--;
      }
      else if (expression != null) {
        if (varargParam != null && pi >= parameters.length) return false;
        PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
        if (exprType == null) return false;
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
        String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
        final ParameterInfoImpl newParameterInfo = new ParameterInfoImpl(-1, name, exprType, expression.getText().replace('\n', ' '));
        result.add(0, newParameterInfo);
        newParams.add(newParameterInfo);
        params.add(0, "<b>" + escapePresentableType(exprType) + "</b>");
        ei--;
      }
    }
    if (result.size() != expressions.length && varargParam == null) return false;
    buf.append(StringUtil.join(params, ", "));
    return true;
  }
}
