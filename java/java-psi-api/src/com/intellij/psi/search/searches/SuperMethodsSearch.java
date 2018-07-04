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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SuperMethodsSearch extends ExtensibleQueryFactory<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.superMethodsSearch");
  private static final SuperMethodsSearch SUPER_METHODS_SEARCH_INSTANCE = new SuperMethodsSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    //null means any class would be matched
    @Nullable private final PsiClass myClass;
    private final boolean myCheckBases;
    private final boolean myAllowStaticMethod;

    public SearchParameters(@NotNull PsiMethod method,
                            @Nullable PsiClass aClass,
                            final boolean checkBases,
                            final boolean allowStaticMethod) {
      myCheckBases = checkBases;
      myClass = aClass;
      myMethod = method;
      myAllowStaticMethod = allowStaticMethod;
    }

    public final boolean isCheckBases() {
      return myCheckBases;
    }

    @NotNull
    public final PsiMethod getMethod() {
      return myMethod;
    }

    @Nullable
    public final PsiClass getPsiClass() {
      return myClass;
    }

    public final boolean isAllowStaticMethod() {
      return myAllowStaticMethod;
    }
  }

  private SuperMethodsSearch() {
  }

  @NotNull
  public static Query<MethodSignatureBackedByPsiMethod> search(@NotNull PsiMethod derivedMethod,
                                                               @Nullable final PsiClass psiClass,
                                                               boolean checkBases,
                                                               boolean allowStaticMethod) {
    final SearchParameters parameters = new SearchParameters(derivedMethod, psiClass, checkBases, allowStaticMethod);
    return SUPER_METHODS_SEARCH_INSTANCE.createUniqueResultsQuery(parameters, MethodSignatureUtil.METHOD_BASED_HASHING_STRATEGY);
  }

}
