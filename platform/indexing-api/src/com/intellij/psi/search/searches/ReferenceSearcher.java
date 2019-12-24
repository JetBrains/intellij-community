// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.model.search.Searcher;
import com.intellij.psi.PsiReference;

/**
 * Convenience interface for easier implementation and finding usages.
 */
public interface ReferenceSearcher extends Searcher<ReferencesSearch.SearchParameters, PsiReference> {
}
