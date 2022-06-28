// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.extensions.ExtensionPointName

interface SEResultsEqualityProvider {
  sealed class SEEqualElementsActionType {
    object DoNothing : SEEqualElementsActionType() {
      override fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType = another
    }
    object Skip : SEEqualElementsActionType() {
      override fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType = if (another is Replace) another else this
    }
    data class Replace(val toBeReplaced: List<SearchEverywhereFoundElementInfo>) : SEEqualElementsActionType() {
      constructor(toBeReplaced: SearchEverywhereFoundElementInfo) : this(listOf(toBeReplaced))

      override fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType =
        if (another is Replace) Replace(toBeReplaced + another.toBeReplaced) else this
    }
    abstract fun combine(another: SEEqualElementsActionType): SEEqualElementsActionType
  }

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
