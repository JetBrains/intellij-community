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
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public class MethodReferencesSearch extends ExtensibleQueryFactory<PsiReference, MethodReferencesSearch.SearchParameters> {
  public static final MethodReferencesSearch INSTANCE = new MethodReferencesSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    private final SearchScope myScope;
    private final boolean myStrictSignatureSearch;

    public SearchParameters(final PsiMethod aClass, SearchScope scope, final boolean strict) {
      myMethod = aClass;
      myScope = scope;
      myStrictSignatureSearch = strict;
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isStrictSignatureSearch() {
      return myStrictSignatureSearch;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private MethodReferencesSearch() {}

  public static Query<PsiReference> search(final PsiMethod method, SearchScope scope, final boolean strictSignatureSearch) {
    //noinspection unchecked
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(method, scope, strictSignatureSearch), TObjectHashingStrategy.CANONICAL, ReferenceDescriptor.MAPPER);
  }

  public static Query<PsiReference> search(final PsiMethod method, final boolean strictSignatureSearch) {
    return search(method, GlobalSearchScope.allScope(method.getProject()), strictSignatureSearch);
  }

  public static Query<PsiReference> search(final PsiMethod method) {
    return search(method, true);
  }
}