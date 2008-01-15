package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;

import java.util.ArrayList;

public class MethodImplementationsSearch implements QueryExecutor<PsiElement, PsiElement> {
  public boolean execute(final PsiElement sourceElement, final Processor<PsiElement> consumer) {
    if (sourceElement instanceof PsiMethod) {
      for (PsiElement implementation : getMethodImplementations((PsiMethod)sourceElement)) {
        if ( ! consumer.process(implementation) ) {
          return false;
        }
      }
    }
    return true;
  }

  public static void getOverridingMethods(PsiMethod method, ArrayList<PsiMethod> list) {
    for (PsiMethod psiMethod : OverridingMethodsSearch.search(method)) {
      list.add(psiMethod);
    }
  }

  public static PsiMethod[] getMethodImplementations(final PsiMethod method) {
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();

    getOverridingMethods(method, result);
    return result.toArray(new PsiMethod[result.size()]);
  }
}
