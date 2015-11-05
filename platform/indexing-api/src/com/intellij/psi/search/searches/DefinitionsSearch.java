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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;

/**
 * Use {@link DefinitionsScopedSearch} instead
 */
@Deprecated()
public class DefinitionsSearch extends ExtensibleQueryFactory<PsiElement, PsiElement> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.definitionsSearch");
  public static DefinitionsSearch INSTANCE = new DefinitionsSearch();

  public static Query<PsiElement> search(PsiElement definitionsOf) {
    return DefinitionsScopedSearch.search(definitionsOf);
  }
}
