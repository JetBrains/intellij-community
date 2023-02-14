// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class SimpleAccessorReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public SimpleAccessorReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    PsiElement refElement = queryParameters.getElementToSearch();
    if (!(refElement instanceof PsiMethod)) return;

    addPropertyAccessUsages((PsiMethod)refElement, queryParameters.getEffectiveSearchScope(), queryParameters.getOptimizer());
  }

  static void addPropertyAccessUsages(@NotNull PsiMethod method, @NotNull SearchScope scope, @NotNull SearchRequestCollector collector) {
    final String propertyName = PropertyUtilBase.getPropertyName(method);
    if (StringUtil.isNotEmpty(propertyName)) {
      SearchScope additional = LocalSearchScope.EMPTY;
      for (CustomPropertyScopeProvider provider : CustomPropertyScopeProvider.EP_NAME.getExtensionList()) {
        additional = additional.union(provider.getScope(method.getProject()));
      }

      SearchScope propScope = scope.intersectWith(method.getUseScope()).intersectWith(additional);
      collector.searchWord(propertyName, propScope, UsageSearchContext.IN_FOREIGN_LANGUAGES, true, method);
    }
  }
}
