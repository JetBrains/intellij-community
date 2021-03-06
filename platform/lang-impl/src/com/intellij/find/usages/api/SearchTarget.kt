// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api

import com.intellij.model.Pointer
import com.intellij.model.search.SearchRequest
import com.intellij.navigation.TargetPresentation
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope

/**
 * Represents the search implementation (the usage handler and the text search strings)
 * plus data needed to display it in the UI.
 */
interface SearchTarget {

  /**
   * @return smart pointer used to restore the [SearchTarget] instance in the subsequent read actions
   */
  fun createPointer(): Pointer<out SearchTarget>

  /**
   * Returning [LocalSearchScope] will also make search scope unavailable to change in the UI.
   * Maximal scope is used to rerun Show Usages if user scope differs from maximal scope.
   *
   * @return maximal search scope where this usage handler might yield any results, or `null` to search everywhere
   */
  @JvmDefault
  val maximalSearchScope: SearchScope?
    get() = null

  /**
   * @return presentation to be displayed in the disambiguation popup
   * when several [different][equals] targets exist to choose from,
   * or in the Usage View (only [icon][TargetPresentation.icon]
   * and [presentable text][TargetPresentation.presentableText] are used)
   */
  val presentation: TargetPresentation

  /**
   * @see UsageHandler.createEmptyUsageHandler
   */
  val usageHandler: UsageHandler<*>

  /**
   * Text doesn't contain references by design (e.g. plain text or markdown),
   * but there might exist occurrences which are feasible to find/rename,
   * e.g fully qualified name of a Java class or package.
   *
   * Returning non-empty collection will enable "Search for text occurrences" checkbox in the UI.
   *
   * @return collection of strings to search for text occurrences
   */
  @JvmDefault
  val textSearchRequests: Collection<SearchRequest>
    get() = emptyList()

  /**
   * Several symbols might have the same usage target;
   * the equals/hashCode are used to remove the same targets from the list.
   */
  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int
}
