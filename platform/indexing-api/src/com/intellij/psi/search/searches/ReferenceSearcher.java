// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.model.search.Searcher;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;

/**
 * Convenience interface for easier implementation and finding usages.
 */
@OverrideOnly
public interface ReferenceSearcher extends Searcher<ReferencesSearch.SearchParameters, PsiReference> {
}
