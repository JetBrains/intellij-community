// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;

/**
 * @deprecated Use {@link DefinitionsScopedSearch} instead
 */
@Deprecated
public final class DefinitionsSearch extends ExtensibleQueryFactory<PsiElement, PsiElement> {
  public static final ExtensionPointName<QueryExecutor<PsiElement, PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.definitionsSearch");
  public static final DefinitionsSearch INSTANCE = new DefinitionsSearch();

  private DefinitionsSearch() {
    super(EP_NAME);
  }

  public static Query<PsiElement> search(PsiElement definitionsOf) {
    return DefinitionsScopedSearch.search(definitionsOf);
  }
}
