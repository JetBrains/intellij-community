// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Equality provider can be used to compare items found by different (or sometimes same) instances of
 * [SearchEverywhereContributor] and decide that those items are pointing to the same entity.
 *
 * For example, following items can be instances of different classes, but the same entities:
 * - Same file returned by [FileSearchEverywhereContributor] and [RecentFilesSEContributor]
 * - Java public class and .java file containing this class
 * - Different links/wrappers to the same [com.intellij.psi.PsiElement]
 * - etc.
 */
interface SEResultsEqualityProvider {

  /**
   * List of possible actions for [compareItems] method.
   */
  sealed class SEEqualElementsActionType {

    /**
     * Nothing to do.
     * Should be used when found item is not equal to any already found one.
     */
    object DoNothing : SEEqualElementsActionType() {
      override fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType = another
    }

    /**
     * New found item should be skipped.
     * Should be used when better presentation of found entity already exists in results.
     */
    object Skip : SEEqualElementsActionType() {
      override fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType = if (another is Replace) another else this
    }

    /**
     * Already existing item `toBeReplaced` should be replaced with the new found item.
     * Should be used when equal item already exists in results but new one represents corresponding entity better.
     */
    data class Replace(val toBeReplaced: List<SearchEverywhereFoundElementInfo>) : SEEqualElementsActionType() {
      constructor(toBeReplaced: SearchEverywhereFoundElementInfo) : this(listOf(toBeReplaced))

      override fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType =
        if (another is Replace) Replace(toBeReplaced + another.toBeReplaced) else this
    }

    abstract fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType
  }

  /**
   * Compare just found [SearchEverywhereFoundElementInfo] with list of already found items and decide how this new item should be handled.
   *
   * See [SEEqualElementsActionType] for possible actions
   *
   * @param newItem new found item. This item is suggested to be added to results list.
   * @param alreadyFoundItems list of already found results. Those items are already shown in results list.
   */
  fun compareItems(newItem: SearchEverywhereFoundElementInfo, alreadyFoundItems: List<SearchEverywhereFoundElementInfo>): SEEqualElementsActionType

  companion object {
    @JvmStatic
    val providers: List<SEResultsEqualityProvider?>
      get() = EP_NAME.extensions.toList()

    @JvmStatic
    fun composite(providers: Collection<SEResultsEqualityProvider>): SEResultsEqualityProvider {
      return object : SEResultsEqualityProvider {
        override fun compareItems(newItem: SearchEverywhereFoundElementInfo,
                                  alreadyFoundItems: List<SearchEverywhereFoundElementInfo>): SEEqualElementsActionType {
          return providers.asSequence()
                   .map { provider: SEResultsEqualityProvider -> provider.compareItems(newItem, alreadyFoundItems) }
                   .firstOrNull { action: SEEqualElementsActionType -> action != SEEqualElementsActionType.DoNothing }
                 ?: SEEqualElementsActionType.DoNothing
        }
      }
    }

    val EP_NAME: ExtensionPointName<SEResultsEqualityProvider> =
      ExtensionPointName.create("com.intellij.searchEverywhereResultsEqualityProvider")
  }
}
