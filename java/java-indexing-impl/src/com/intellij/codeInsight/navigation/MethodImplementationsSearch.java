/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MethodImplementationsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull final Processor<PsiElement> consumer) {
    final PsiElement sourceElement = queryParameters.getElement();
    if (sourceElement instanceof PsiMethod) {
      return processImplementations((PsiMethod)sourceElement, consumer, queryParameters.getScope());
    }
    return true;
  }

  public static boolean processImplementations(final PsiMethod psiMethod, final Processor<PsiElement> consumer,
                                               final SearchScope searchScope) {
    return processOverridingMethods(psiMethod, searchScope, consumer::process) &&
           FunctionalExpressionSearch.search(psiMethod, searchScope).forEach((Processor<PsiFunctionalExpression>)consumer::process);
  }

  @SuppressWarnings("unused")
  public static void getOverridingMethods(PsiMethod method, List<PsiMethod> list, SearchScope scope) {
    processOverridingMethods(method, scope, new CommonProcessors.CollectProcessor<>(list));
  }

  private static boolean processOverridingMethods(PsiMethod method, SearchScope scope, Processor<PsiMethod> processor) {
    return OverridingMethodsSearch.search(method, scope, true).forEach(processor);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  public static PsiMethod[] getMethodImplementations(final PsiMethod method, SearchScope scope) {
    List<PsiMethod> result = new ArrayList<>();
    processOverridingMethods(method, scope, new CommonProcessors.CollectProcessor<>(result));
    return result.toArray(new PsiMethod[result.size()]);
  }
}
