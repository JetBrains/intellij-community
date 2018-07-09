// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MethodImplementationsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiElement> consumer) {
    final PsiElement sourceElement = queryParameters.getElement();
    if (sourceElement instanceof PsiMethod) {
      return processImplementations((PsiMethod)sourceElement, consumer, queryParameters.getScope());
    }
    return true;
  }

  public static boolean processImplementations(final PsiMethod psiMethod, final Processor<? super PsiElement> consumer,
                                               final SearchScope searchScope) {
    return processOverridingMethods(psiMethod, searchScope, consumer) &&
           FunctionalExpressionSearch.search(psiMethod, searchScope).forEach(consumer);
  }

  public static void getOverridingMethods(PsiMethod method, List<PsiMethod> list, SearchScope scope) {
    processOverridingMethods(method, scope, new CommonProcessors.CollectProcessor<>(list));
  }

  private static boolean processOverridingMethods(PsiMethod method, SearchScope scope, Processor<? super PsiMethod> processor) {
    return OverridingMethodsSearch.search(method, scope, true).forEach(processor);
  }
}
