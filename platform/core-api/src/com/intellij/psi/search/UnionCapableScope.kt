// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface in your [GlobalSearchScope] to allow merging with other scopes.
 *
 * If a scope implements this interface, [GlobalSearchScope.uniteWith] and [GlobalSearchScope.union] will use it.
 */
@ApiStatus.Experimental
interface UnionCapableScope {
  /**
   * @return [UnionResult] if the scopes can be merged or `null` otherwise.
   */
  fun uniteWith(scopes: Collection<GlobalSearchScope>): UnionResult?

  data class UnionResult(
    val unitedScope: GlobalSearchScope,
    val skippedScopes: Set<GlobalSearchScope>,
  )
}
