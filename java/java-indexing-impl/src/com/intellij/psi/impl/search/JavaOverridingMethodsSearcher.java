package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
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
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class JavaOverridingMethodsSearcher implements QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final OverridingMethodsSearch.SearchParameters p, @NotNull final Processor<PsiMethod> consumer) {
    final PsiMethod method = p.getMethod();
    final SearchScope scope = p.getScope();

    final PsiClass parentClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      @Override
      public PsiClass compute() {
        return method.getContainingClass();
      }
    });
    assert parentClass != null;
    Processor<PsiClass> inheritorsProcessor = new Processor<PsiClass>() {
      @Override
      public boolean process(final PsiClass inheritor) {
        PsiMethod found = ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod>() {
          @Override
          @Nullable
          public PsiMethod compute() {
            return findOverridingMethod(inheritor, parentClass, method);
          }
        });
        return found == null || consumer.process(found) && p.isCheckDeep();
      }
    };

    return ClassInheritorsSearch.search(parentClass, scope, true).forEach(inheritorsProcessor);
  }

  @Nullable
  private static PsiMethod findOverridingMethod(PsiClass inheritor, @NotNull PsiClass parentClass, PsiMethod method) {
    PsiSubstitutor substitutor = inheritor.isInheritor(parentClass, true) ?
                                 TypeConversionUtil.getSuperClassSubstitutor(parentClass, inheritor, PsiSubstitutor.EMPTY) :
                                 PsiSubstitutor.EMPTY;
    MethodSignature signature = method.getSignature(substitutor);
    PsiMethod found = MethodSignatureUtil.findMethodBySuperSignature(inheritor, signature, false);
    if (found != null && isAcceptable(found, method)) {
      return found;
    }

    if (parentClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
      final PsiClass superClass = inheritor.getSuperClass();
      if (superClass != null && !superClass.isInheritor(parentClass, true)) {
        PsiMethod derived = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
        if (derived != null && isAcceptable(derived, method)) {
          return derived;
        }
      }
    }
    return null;
  }

  private static boolean isAcceptable(final PsiMethod found, final PsiMethod method) {
    return !found.hasModifierProperty(PsiModifier.STATIC) &&
           (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
            JavaPsiFacade.getInstance(found.getProject())
              .arePackagesTheSame(method.getContainingClass(), found.getContainingClass()));
  }
}
