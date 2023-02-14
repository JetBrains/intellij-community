// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MatchUtil {
  @Nullable
  public static String getChangedSignature(Match match, final PsiMethod method, final boolean shouldBeStatic, String visibility) {
    final PsiType returnType = match.getChangedReturnType(method);
    if (!match.myChangedParams.isEmpty() || returnType != null) {
      @NonNls StringBuilder buffer = new StringBuilder();
      buffer.append(visibility);
      if (buffer.length() > 0) {
        buffer.append(" ");
      }
      if (shouldBeStatic) {
        buffer.append("static ");
      }
      final PsiTypeParameterList typeParameterList = method.getTypeParameterList();
      if (typeParameterList != null) {
        buffer.append(typeParameterList.getText());
        buffer.append(" ");
      }

      buffer.append(PsiFormatUtil.formatType(returnType != null ? returnType : method.getReturnType(), 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(method.getName());
      buffer.append("(");
      int count = 0;
      final String INDENT = "    ";
      final List<ParameterInfoImpl> params = patchParams(match.myChangedParams, method);
      for (ParameterInfoImpl param : params) {
        String typeText = param.getTypeText();
        if (count > 0) {
          buffer.append(",");
        }
        buffer.append("\n");
        buffer.append(INDENT);
        buffer.append(typeText);
        buffer.append(" ");
        buffer.append(param.getName());
        count++;
      }

      if (count > 0) {
        buffer.append("\n");
      }
      buffer.append(")");
      final PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
      if (exceptions.length > 0) {
        buffer.append("\n");
        buffer.append("throws\n");
        for (PsiType exception : exceptions) {
          buffer.append(INDENT);
          buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
          buffer.append("\n");
        }
      }
      return buffer.toString();
    }
    return null;
  }

  public static void changeSignature(@NotNull Match match, @NotNull PsiMethod psiMethod) {
    final PsiType expressionType = match.getChangedReturnType(psiMethod);
    if (expressionType == null && match.myChangedParams.isEmpty()) return;
    final List<ParameterInfoImpl> newParameters = patchParams(match.myChangedParams, psiMethod);
    final ChangeSignatureProcessor csp = new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod, false, null, psiMethod.getName(),
                                                                      expressionType != null ? expressionType : psiMethod.getReturnType(),
                                                                      newParameters.toArray(new ParameterInfoImpl[0]));

    csp.run();
  }

  public static List<ParameterInfoImpl> patchParams(Map<PsiVariable, PsiType> changedParams, final PsiMethod psiMethod) {
    final ArrayList<ParameterInfoImpl> newParameters = new ArrayList<>();
    final PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
    for (int i = 0; i < oldParameters.length; i++) {
      final PsiParameter oldParameter = oldParameters[i];
      PsiType type = oldParameter.getType();
      for (PsiVariable variable : changedParams.keySet()) {
        if (PsiEquivalenceUtil.areElementsEquivalent(variable, oldParameter)) {
          type = changedParams.get(variable);
          break;
        }
      }
      newParameters.add(ParameterInfoImpl.create(i).withName(oldParameter.getName()).withType(type));
    }
    return newParameters;
  }
}
