// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LambdaHighlightingUtil {
  // 15.13 | 15.27
  // It is a compile-time error if any class or interface mentioned by either U or the function type of U
  // is not accessible from the class or interface in which the method reference expression appears.
  static HighlightInfo.Builder checkFunctionalInterfaceTypeAccessible(@NotNull Project project,
                                                                      @NotNull PsiFunctionalExpression expression,
                                                                      @NotNull PsiType functionalInterfaceType) {
    return checkFunctionalInterfaceTypeAccessible(project, expression, functionalInterfaceType, true);
  }

  private static HighlightInfo.Builder checkFunctionalInterfaceTypeAccessible(@NotNull Project project, @NotNull PsiFunctionalExpression expression,
                                                                              @NotNull PsiType functionalInterfaceType,
                                                                              boolean checkFunctionalTypeSignature) {
    PsiClassType.ClassResolveResult resolveResult =
      PsiUtil.resolveGenericsClassInType(PsiClassImplUtil.correctType(functionalInterfaceType, expression.getResolveScope()));
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) {
      return null;
    }
    if (PsiUtil.isAccessible(project, psiClass, expression, null)) {
      for (PsiType type : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
        if (type != null) {
          HighlightInfo.Builder info = checkFunctionalInterfaceTypeAccessible(project, expression, type, false);
          if (info != null) {
            return info;
          }
        }
      }

      PsiMethod psiMethod = checkFunctionalTypeSignature ? LambdaUtil.getFunctionalInterfaceMethod(resolveResult) : null;
      if (psiMethod != null) {
        PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(psiMethod, resolveResult);
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
          PsiType substitute = substitutor.substitute(parameter.getType());
          if (substitute != null) {
            HighlightInfo.Builder info = checkFunctionalInterfaceTypeAccessible(project, expression, substitute, false);
            if (info != null) {
              return info;
            }
          }
        }

        PsiType substitute = substitutor.substitute(psiMethod.getReturnType());
        if (substitute != null) {
          return checkFunctionalInterfaceTypeAccessible(project, expression, substitute, false);
        }
        return null;
      }
    }
    else {
      Pair<@Nls String, List<IntentionAction>> problem =
        HighlightUtil.accessProblemDescriptionAndFixes(expression, psiClass, resolveResult);
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(problem.first);
      if (problem.second != null) {
        problem.second.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
      }
      return info;
    }

    final ErrorWithFixes moduleProblem = HighlightUtil.checkModuleAccess(psiClass, expression, resolveResult);
    if (moduleProblem != null) {
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(moduleProblem.message);
      moduleProblem.fixes.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
      return info;
    }

    return null;
  }
}
