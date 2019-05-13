// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ConstructorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters p, @NotNull Processor<? super PsiReference> consumer) {
    final PsiElement element = p.getElementToSearch();
    if (!(element instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod)element;
    final PsiManager[] manager = new PsiManager[1];
    PsiClass aClass = ReadAction.compute(() -> {
      if (!method.isConstructor()) return null;
      PsiClass aClass1 = method.getContainingClass();
      manager[0] = aClass1 == null ? null : aClass1.getManager();
      return aClass1;
    });
    if (manager[0] == null) {
      return;
    }
    SearchScope scope = ReadAction.compute(() -> p.getEffectiveSearchScope());
    new ConstructorReferencesSearchHelper(manager[0])
      .processConstructorReferences(consumer, method, aClass, scope, p.getProject(), p.isIgnoreAccessScope(), true, p.getOptimizer());
  }
}
