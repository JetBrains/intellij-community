// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.stream.Stream;

public final class MethodChainsSearchUtil {
  private MethodChainsSearchUtil() {
  }

  public static @Nullable PsiMethod getMethodWithMinNotPrimitiveParameters(PsiMethod @NotNull [] methods,
                                                                           @NotNull PsiClass target) {
    return Stream.of(methods)
      .filter(m -> {
        for (PsiParameter parameter : m.getParameterList().getParameters()) {
          PsiType t = parameter.getType();
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(t);
          if (aClass == target) {
            return false;
          }
        }
        return true;
      }).min(Comparator.comparing(MethodChainsSearchUtil::getNonPrimitiveParameterCount)).orElse(null);
  }

  private static int getNonPrimitiveParameterCount(PsiMethod method) {
    return (int)Stream.of(method.getParameterList().getParameters())
      .map(p -> p.getType())
      .filter(t -> !TypeConversionUtil.isPrimitiveAndNotNull(t))
      .count();
  }
}
