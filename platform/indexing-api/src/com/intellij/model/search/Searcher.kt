// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search

import com.intellij.util.Query
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.OverrideOnly

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
   * @return read-only collection of results, which can be obtained from [parameters],
   * meaning they don't require indexes or otherwise long-running computations
   */
  @RequiresReadLock
  fun collectImmediateResults(parameters: P): Collection<@JvmWildcard R> {
    return emptyList()
  }

  /**
   * @return read-only collection of queries to be executed when the search is run with [parameters]
   * @see collectSearchRequest
   */
  @RequiresReadLock
  fun collectSearchRequests(parameters: P): Collection<@JvmWildcard Query<out R>> {
    return listOfNotNull(collectSearchRequest(parameters))
  }

  /**
   * This function exists for convenience of a searcher implementation which yields zero (`null`) or one additional query.
   *
   * @return query to be executed when the search is run with [parameters]
   */
  @OverrideOnly
  @RequiresReadLock
  fun collectSearchRequest(parameters: P): Query<out R>? {
    return null
  }
}
