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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public class ClassImplementationsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull Processor<PsiElement> consumer) {
    final PsiElement sourceElement = queryParameters.getElement();
    return !(sourceElement instanceof PsiClass) || processImplementations((PsiClass)sourceElement, consumer, queryParameters.getScope());
  }

  public static boolean processImplementations(final PsiClass psiClass, final Processor<PsiElement> processor, SearchScope scope) {
    final boolean showInterfaces = Registry.is("ide.goto.implementation.show.interfaces");
    if (!ClassInheritorsSearch.search(psiClass, scope, true).forEach(new PsiElementProcessorAdapter<>(element -> {
      if (!showInterfaces && element.isInterface()) {
        return true;
      }
      return processor.process(element);
    }))) {
      return false;
    }

    return FunctionalExpressionSearch.search(psiClass, scope).forEach((Processor<PsiFunctionalExpression>)processor::process);
  }
}
