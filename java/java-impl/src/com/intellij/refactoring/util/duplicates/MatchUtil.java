/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public class MatchUtil {
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
                                                                      newParameters.toArray(new ParameterInfoImpl[newParameters.size()]));

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
      newParameters.add(new ParameterInfoImpl(i, oldParameter.getName(), type));
    }
    return newParameters;
  }
}
