package com.intellij.psi.impl.search;

import com.intellij.psi.PsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class MethodDeepestSuperSearcher implements QueryExecutor<PsiMethod, PsiMethod> {
  @Override
  public boolean execute(@NotNull PsiMethod method, @NotNull Processor<PsiMethod> consumer) {
    final Set<PsiMethod> methods = new THashSet<PsiMethod>();
    methods.add(method);
    return findDeepestSuperOrSelfSignature(method, methods, null, consumer);
  }

  private static boolean findDeepestSuperOrSelfSignature(PsiMethod method,
                                                         Set<PsiMethod> set,
                                                         Set<PsiMethod> guard,
                                                         Processor<PsiMethod> processor) {
    if (guard != null && !guard.add(method)) return true;
    PsiMethod[] supers = method.findSuperMethods();

    if (supers.length == 0 && set.add(method) && !processor.process(method)) {
      return false;
    }
    for (PsiMethod superMethod : supers) {
      if (guard == null) {
        guard = new THashSet<PsiMethod>();
        guard.add(method);
      }
      if (!findDeepestSuperOrSelfSignature(superMethod, set, guard, processor)) return false;
    }
    return true;
  }
}
