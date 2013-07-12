package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class MethodChainsSearchUtil {
  private MethodChainsSearchUtil() {
  }

  @Nullable
  public static PsiMethod getMethodWithMinNotPrimitiveParameters(final @NotNull PsiMethod[] methods,
                                                                 final Set<String> excludedParamsQNames) {
    PsiMethod minMethod = null;
    int minParametersCount = Integer.MAX_VALUE;
    for (final PsiMethod method : methods) {
      final PsiParameterList parameterList = method.getParameterList();
      boolean doContinue = false;
      int parametersCount = parameterList.getParametersCount();
      for (final PsiParameter p : parameterList.getParameters()) {
        if (!(p.getType() instanceof PsiPrimitiveType)) {
          if (excludedParamsQNames.contains(p.getType().getCanonicalText())) {
            doContinue = true;
            break;
          }
          parametersCount++;
        }
      }
      if (doContinue) {
        continue;
      }
      if (parametersCount < minParametersCount) {
        if (parametersCount == 0) {
          return method;
        }
        minParametersCount = parametersCount;
        minMethod = method;
      }
    }
    return minMethod;
  }

  public static boolean checkParametersForTypesQNames(final PsiMethod[] psiMethods, final Set<String> excludedTypesQNames) {
    if (psiMethods.length == 0) {
      return true;
    }
    for (final PsiMethod method : psiMethods) {
      boolean hasTargetInParams = false;
      for (final PsiParameter param : method.getParameterList().getParameters()) {
        final String paramType = param.getType().getCanonicalText();
        if (excludedTypesQNames.contains(paramType)) {
          hasTargetInParams = true;
          break;
        }
      }
      if (!hasTargetInParams) {
        return true;
      }
    }
    return false;
  }

  public static <T> HashSet<T> unionToHashSet(final Collection<T> collection, final T... items) {
    final HashSet<T> result = new HashSet<T>();
    result.addAll(collection);
    Collections.addAll(result, items);
    return result;
  }
}
