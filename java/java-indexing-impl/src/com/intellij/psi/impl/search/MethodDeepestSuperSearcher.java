// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class MethodDeepestSuperSearcher implements QueryExecutor<PsiMethod, PsiMethod> {
  @Override
  public boolean execute(@NotNull PsiMethod method, @NotNull Processor<? super PsiMethod> consumer) {
    return processDeepestSuperMethods(method, consumer);
  }

  public static boolean processDeepestSuperMethods(@NotNull PsiMethod method, @NotNull Processor<? super PsiMethod> consumer) {
    final Set<PsiMethod> methods = new HashSet<>();
    methods.add(method);
    return findDeepestSuperOrSelfSignature(method, methods, null, consumer);
  }

  private static boolean findDeepestSuperOrSelfSignature(@NotNull PsiMethod method,
                                                         @NotNull Set<? super PsiMethod> set,
                                                         Set<? super PsiMethod> guard,
                                                         @NotNull Processor<? super PsiMethod> processor) {
    if (guard != null && !guard.add(method)) return true;
    PsiMethod[] supers = ReadAction.compute(() -> method.findSuperMethods());

    if (supers.length == 0 && set.add(method) && !processor.process(method)) {
      return false;
    }
    for (PsiMethod superMethod : supers) {
      if (guard == null) {
        guard = new HashSet<>();
        guard.add(method);
      }
      if (!findDeepestSuperOrSelfSignature(superMethod, set, guard, processor)) return false;
    }
    return true;
  }
}
