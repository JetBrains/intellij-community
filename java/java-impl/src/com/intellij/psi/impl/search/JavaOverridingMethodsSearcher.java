package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaOverridingMethodsSearcher implements QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  public boolean execute(@NotNull final OverridingMethodsSearch.SearchParameters p, @NotNull final Processor<PsiMethod> consumer) {
    final PsiMethod method = p.getMethod();
    final SearchScope scope = p.getScope();

    final PsiClass parentClass = method.getContainingClass();

    Processor<PsiClass> inheritorsProcessor = new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(parentClass, inheritor, PsiSubstitutor.EMPTY);
        MethodSignature signature = method.getSignature(substitutor);
        PsiMethod found = MethodSignatureUtil.findMethodBySuperSignature(inheritor, signature, false);
        if (found == null || !isAcceptable(found, method)) {
          if (parentClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
            final PsiClass superClass = inheritor.getSuperClass();
            if (superClass != null && !superClass.isInheritor(parentClass, true)) {
              found = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
              if (found != null && isAcceptable(found, method)) {
                return consumer.process(found) && p.isCheckDeep();
              }
            }
          }
          return true;
        }
        return consumer.process(found) && p.isCheckDeep();
      }
    };

    return ClassInheritorsSearch.search(parentClass, scope, true).forEach(inheritorsProcessor);
  }

  private static boolean isAcceptable(final PsiMethod found, final PsiMethod method) {
    return !found.hasModifierProperty(PsiModifier.STATIC) &&
           (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
            JavaPsiFacade.getInstance(found.getProject())
              .arePackagesTheSame(method.getContainingClass(), found.getContainingClass()));
  }
}
