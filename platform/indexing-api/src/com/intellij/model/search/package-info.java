// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/**
 * <h3>Overview</h3>
 * <ul>
 * <li>the search starts from {@link com.intellij.model.Symbol symbol}</li>
 * <li>each symbol might be referenced in different scopes via different names</li>
 * </ul>
 *
 * <h3>Search scopes</h3>
 * Effective search scope is the actual scope where the search is performed.
 * Effective search scope is an intersection of the following scopes.
 * <ul>
 * <li>Input scope: the scope selected by the user in the UI or in some cases by the platform
 * (e.g. local file scope when searching for references to highlight)</li>
 * <li>Language scope: all files with language X, where the language is
 * {@linkplain com.intellij.model.search.CodeReferenceSearcher#getReferencingLanguage CodeReferenceSearcher#getReferencingLanguage}
 * for code references and
 * {@linkplain com.intellij.model.psi.PsiSymbolReferenceProviderBean#hostLanguage PsiSymbolReferenceProviderBean#hostLanguage}
 * for external references</li>
 * <li>Restriction scope: additional scope which further restricts input and language scopes,
 * e.g. {@linkplain com.intellij.model.search.SearchRequest#getSearchScope SearchRequest#getSearchScope}</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.intellij.model.search;

import org.jetbrains.annotations.ApiStatus;
