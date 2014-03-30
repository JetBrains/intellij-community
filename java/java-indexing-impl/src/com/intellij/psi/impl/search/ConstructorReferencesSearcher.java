package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ConstructorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
    final PsiElement element = p.getElementToSearch();
    if (!(element instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod)element;
    final PsiManager[] manager = new PsiManager[1];
    PsiClass aClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      public PsiClass compute() {
        if (!method.isConstructor()) return null;
        PsiClass aClass = method.getContainingClass();
        manager[0] = aClass == null ? null : aClass.getManager();
        return aClass;
      }
    });
    if (manager[0] == null) {
      return;
    }
    new ConstructorReferencesSearchHelper(manager[0])
      .processConstructorReferences(consumer, method, aClass, p.getScope(), p.isIgnoreAccessScope(), true, p.getOptimizer());
  }
}
