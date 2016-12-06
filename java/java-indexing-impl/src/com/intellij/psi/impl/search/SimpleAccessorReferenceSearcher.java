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
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class SimpleAccessorReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public SimpleAccessorReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
    PsiElement refElement = queryParameters.getElementToSearch();
    if (!(refElement instanceof PsiMethod)) return;

    addPropertyAccessUsages((PsiMethod)refElement, queryParameters.getEffectiveSearchScope(), queryParameters.getOptimizer());
  }

  static void addPropertyAccessUsages(@NotNull PsiMethod method, @NotNull SearchScope scope, @NotNull SearchRequestCollector collector) {
    final String propertyName = PropertyUtil.getPropertyName(method);
    if (StringUtil.isNotEmpty(propertyName)) {
      SearchScope additional = GlobalSearchScope.EMPTY_SCOPE;
      for (CustomPropertyScopeProvider provider : Extensions.getExtensions(CustomPropertyScopeProvider.EP_NAME)) {
        additional = additional.union(provider.getScope(method.getProject()));
      }

      SearchScope propScope = scope.intersectWith(method.getUseScope()).intersectWith(additional);
      if (propScope instanceof GlobalSearchScope) {
        // optimisation: java method accessors can be referenced by simple property name from non-java only
        propScope = GlobalSearchScope.removeFileTypesFromScope((GlobalSearchScope)propScope, JavaFileType.INSTANCE);
      }
      collector.searchWord(propertyName, propScope, UsageSearchContext.IN_FOREIGN_LANGUAGES, true, method);
    }
  }
}
