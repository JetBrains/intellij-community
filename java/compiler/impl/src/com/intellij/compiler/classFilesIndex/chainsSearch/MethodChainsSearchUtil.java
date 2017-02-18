/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import com.intellij.psi.*;
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

  public static <T> HashSet<T> joinToHashSet(final Collection<T> collection, final T... items) {
    final HashSet<T> result = new HashSet<>();
    result.addAll(collection);
    Collections.addAll(result, items);
    return result;
  }
}
