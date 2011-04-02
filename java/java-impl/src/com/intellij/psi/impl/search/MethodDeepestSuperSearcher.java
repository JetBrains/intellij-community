package com.intellij.psi.impl.search;

import com.intellij.psi.PsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class MethodDeepestSuperSearcher implements QueryExecutor<PsiMethod, PsiMethod> {
  public boolean execute(@NotNull final PsiMethod method, @NotNull final Processor<PsiMethod> consumer) {
    final Set<PsiMethod> methods = new LinkedHashSet<PsiMethod>();
    findDeepestSuperOrSelfSignature(method, methods, null);
    for (final PsiMethod psiMethod : methods) {
      if (psiMethod != method && !consumer.process(psiMethod)) {
        return false;
      }
    }
    return true;
  }

  private static void findDeepestSuperOrSelfSignature(PsiMethod method, final Set<PsiMethod> set, Set<PsiMethod> guard) {
    if (guard != null && !guard.add(method)) return;
    PsiMethod[] supers = method.findSuperMethods();

    if (supers.length == 0) {
      set.add(method);
    }
    else {
      for (PsiMethod superMethod : supers) {
        if (guard == null) guard = new THashSet<PsiMethod>();
        findDeepestSuperOrSelfSignature(superMethod, set, guard);
      }
    }
  }
}
