/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.PsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class MethodDeepestSuperSearcher implements QueryExecutor<PsiMethod, PsiMethod> {

  public boolean execute(final PsiMethod method, final Processor<PsiMethod> consumer) {
    final Set<PsiMethod> methods = new LinkedHashSet<PsiMethod>();
    findDeepestSuperOrSelfSignature(method, methods);
    for (final PsiMethod psiMethod : methods) {
      if (psiMethod != method && !consumer.process(psiMethod)) {
        return false;
      }
    }
    return true;
  }

  private static void findDeepestSuperOrSelfSignature(PsiMethod method, final Set<PsiMethod> set) {
    PsiMethod[] supers = method.findSuperMethods();

    if (supers.length == 0) {
      set.add(method);
      return;
    }

    for (PsiMethod superMethod : supers) {
      findDeepestSuperOrSelfSignature(superMethod, set);
    }
  }

}
