// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class OverloadedVarargsMethodInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInconvertibleTypes = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInconvertibleTypes", InspectionGadgetsBundle.message(
        "overloaded.vararg.method.problem.option")));
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethod element = (PsiMethod)infos[0];
    if (element.isConstructor()) {
      return InspectionGadgetsBundle.message(
        "overloaded.vararg.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "overloaded.vararg.method.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverloadedVarargMethodVisitor();
  }

  private class OverloadedVarargMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.isVarArgs()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String methodName = method.getName();
      final PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, true);
      for (PsiMethod sameNameMethod : sameNameMethods) {
        PsiClass superClass = sameNameMethod.getContainingClass();
        PsiSubstitutor substitutor = superClass != null ? TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY)
                                                        : PsiSubstitutor.EMPTY;
        if (!MethodSignatureUtil.areSignaturesEqual(sameNameMethod.getSignature(substitutor),
                                                    method.getSignature(PsiSubstitutor.EMPTY))) {
          if (ignoreInconvertibleTypes && !areConvertibleTypesWithVarArgs(method.getParameterList(),
                                                                          sameNameMethod.getParameterList())) {
            continue;
          }
          registerMethodError(method, method);
          return;
        }
      }
    }

    private static boolean areConvertibleTypesWithVarArgs(@NotNull PsiParameterList parameterListWithVarArgs,
                                                          @NotNull PsiParameterList otherParameterList) {
      PsiParameter[] parametersWithVarArgs = parameterListWithVarArgs.getParameters();
      PsiParameter[] otherParameters = otherParameterList.getParameters();

      int lengthForVarArgs = parametersWithVarArgs.length;
      int otherLength = otherParameters.length;

      //example:
      //parameterListWithVarArgs: (Integer i1, Integer i2, String... strings)
      //otherParameterList: (Integer i1)
      if (lengthForVarArgs > otherLength + 1) {
        return false;
      }

      for (int i = 0; i < otherLength; i++) {
        PsiType type = i < lengthForVarArgs ? getTypeForComparison(parametersWithVarArgs[i]) :
                       getTypeForComparison(parametersWithVarArgs[lengthForVarArgs - 1]);

        PsiType otherType = getTypeForComparison(otherParameters[i]);

        if (!type.isConvertibleFrom(otherType) && !otherType.isConvertibleFrom(type)) {
          return false;
        }
      }

      return true;
    }
  }

  private static PsiType getTypeForComparison(PsiParameter parameter) {
    PsiType type = parameter.getType();
    return type instanceof PsiEllipsisType ellipsisType ? ellipsisType.getComponentType() : type;
  }
}