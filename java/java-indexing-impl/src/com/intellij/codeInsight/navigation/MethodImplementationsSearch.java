/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class MethodImplementationsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull Processor<PsiElement> consumer) {
    final PsiElement sourceElement = queryParameters.getElement();
    if (sourceElement instanceof PsiMethod) {
      PsiMethod[] implementations = getMethodImplementations((PsiMethod)sourceElement, queryParameters.getScope());
      return ContainerUtil.process(implementations, consumer);
    }
    return true;
  }

  public static void getOverridingMethods(PsiMethod method, ArrayList<PsiMethod> list, SearchScope scope) {
    for (PsiMethod psiMethod : OverridingMethodsSearch.search(method, scope, true)) {
      list.add(psiMethod);
    }
  }

  public static PsiMethod[] getMethodImplementations(final PsiMethod method, SearchScope scope) {
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();

    getOverridingMethods(method, result, scope);
    return result.toArray(new PsiMethod[result.size()]);
  }
}
