// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeMethodSignatureFromUsageReverseOrderFix extends ChangeMethodSignatureFromUsageFix {
  public ChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                       PsiExpression @NotNull [] expressions,
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
                                       HashSet<? super ParameterInfoImpl> newParams,
                                       PsiParameter[] parameters,
                                       List<? super ParameterInfoImpl> result) {
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
        result.add(0, ParameterInfoImpl.create(pi).withName(parameter.getName()).withType(type));
        params.add(0, escapePresentableType(type));
        pi--;
        ei--;
      }
      else if (isArgumentInVarargPosition(expressions, ei, varargParam, substitutor)) {
        if (pi == parameters.length - 1) {
          final PsiType type = varargParam.getType();
          result.add(0, ParameterInfoImpl.create(pi).withName(varargParam.getName()).withType(type));
          params.add(0, escapePresentableType(type));
        }
        pi--;
        ei--;
      }
      else if (expression != null) {
        if (varargParam != null && pi >= parameters.length) return false;
        PsiType exprType = CommonJavaRefactoringUtil.getTypeByExpression(expression);
        if (exprType == null) return false;
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
        String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
        final ParameterInfoImpl newParameterInfo = ParameterInfoImpl.createNew()
          .withName(name)
          .withType(exprType)
          .withDefaultValue(expression.getText().replace('\n', ' '));
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
