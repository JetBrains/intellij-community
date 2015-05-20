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

package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * The search is used in two IDE navigation functions namely Go To Implementation (Ctrl+Alt+B) and
 * Quick View Definition (Ctrl+Shift+I). Default searchers produce implementing/overriding methods if the method
 * have been searched and class inheritors for the class.
 *
 */
public class DefinitionsScopedSearch extends ExtensibleQueryFactory<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.definitionsScopedSearch");
  public static final DefinitionsScopedSearch INSTANCE = new DefinitionsScopedSearch();
  
  static {
    final QueryExecutor[] OLD_EXECUTORS = DefinitionsSearch.EP_NAME.getExtensions();
    for (final QueryExecutor executor : OLD_EXECUTORS) {
      INSTANCE.registerExecutor(new QueryExecutor<PsiElement, SearchParameters>() {
        @Override
        public boolean execute(@NotNull SearchParameters queryParameters, @NotNull Processor<PsiElement> consumer) {
          return executor.execute(queryParameters.getElement(), consumer);
        }
      });
    }
 }


  public static Query<PsiElement> search(PsiElement definitionsOf) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(definitionsOf));
  }
  
  public static Query<PsiElement> search(PsiElement definitionsOf, SearchScope searchScope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(definitionsOf, searchScope, true));
  }
  
  public static class SearchParameters {
    private final PsiElement myElement;
    private final SearchScope myScope;
    private final boolean myCheckDeep;

    public SearchParameters(@NotNull final PsiElement element) {
      this(element, ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
        @Override
        public SearchScope compute() {
          return element.getUseScope();
        }
      }), true);
    }

    public SearchParameters(@NotNull PsiElement element, @NotNull SearchScope scope, final boolean checkDeep) {
      myElement = element;
      myScope = scope;
      myCheckDeep = checkDeep;
    }

    @NotNull
    public PsiElement getElement() {
      return myElement;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    @NotNull
    public SearchScope getScope() {
      return ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
        @Override
        public SearchScope compute() {
          return myScope.intersectWith(PsiSearchHelper.SERVICE.getInstance(myElement.getProject()).getUseScope(myElement));
        }
      });
    }
  }

}
