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
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.text.EditDistance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public final class MethodChainsSearchUtil {
  private final static int COMMON_PART_MIN_LENGTH = 3;

  private MethodChainsSearchUtil() {
  }

  public static boolean isSimilar(@NotNull final String target,
                                  @NotNull final String candidate) {
    return EditDistance.levenshtein(target, sanitizedToLowerCase(candidate), true) >= COMMON_PART_MIN_LENGTH;
  }

  @NotNull
  public static String sanitizedToLowerCase(@NotNull final String name) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      final char ch = name.charAt(i);
      if (Character.isLetter(ch)) {
        result.append(Character.toLowerCase(ch));
      }
    }
    return result.toString();
  }

  @Nullable
  public static PsiMethod getMethodWithMinNotPrimitiveParameters(final @NotNull PsiMethod[] methods,
                                                                 final @NotNull Set<String> excludedQNames) {
    return Stream.of(methods)
      .filter(m -> !containsParameter(m, excludedQNames))
      .sorted(Comparator.comparing(MethodChainsSearchUtil::getNonPrimitiveParameterCount))
      .findFirst().orElse(null);
  }

  private static boolean containsParameter(@NotNull PsiMethod method, @NotNull Set<String> excludedQNames) {
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiType t = parameter.getType();
      final boolean matched = isRawTypeOneOf(t, excludedQNames);
      if (matched) {
        return false;
      }
    }
    return true;
  }

  private static boolean isRawTypeOneOf(@NotNull PsiType type, @NotNull Set<String> qNames) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return aClass != null && qNames.contains(aClass.getQualifiedName());
  }

  private static int getNonPrimitiveParameterCount(PsiMethod method) {
    return (int)Stream.of(method.getParameterList().getParameters())
      .map(p -> p.getType())
      .filter(t -> !TypeConversionUtil.isPrimitiveAndNotNull(t))
      .count();
  }

  public static boolean doesMethodsContainParameters(@NotNull final PsiMethod[] psiMethods,
                                                     @NotNull final Set<PsiType> parameterRawTypes) {
    for (PsiMethod m : psiMethods) {
      //TODO
      //if (!containsParameter(m, parameterRawTypes)) {
      //  return true;
      //}
    }
    return false;
  }
}
