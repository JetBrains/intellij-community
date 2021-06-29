// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search

import com.intellij.util.Query

/**
 * Example:
 * When searching for references to some getter method 'getFoo()',
 * we also want to include property references 'foo' from some XMLs.
 * In this case we order to pass all references with 'foo' text that resolve to 'getFoo()' into original processor.
 *
 * Implementations should be registered at `com.intellij.searcher` extension point.
 *
 * It's highly advised to use [SearchService] methods to build additional queries.
 *
 * @see PsiSymbolReferenceSearcher
 * @see PsiSymbolDeclarationSearcher
 */
interface Searcher<P : SearchParameters<R>, R : Any> {

  /**
   * @return read-only collection of queries to be executed when the search is run with `parameters`
   */
  fun collectSearchRequests(parameters: P): Collection<@JvmWildcard Query<out R>>
}
