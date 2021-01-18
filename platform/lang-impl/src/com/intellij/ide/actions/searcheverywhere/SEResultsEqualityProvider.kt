// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import java.util.*

interface SEResultsEqualityProvider {
  enum class SEEqualElementsActionType {
    DO_NOTHING, SKIP, REPLACE
  }

  fun compareItems(newItem: SearchEverywhereFoundElementInfo, alreadyFoundItem: SearchEverywhereFoundElementInfo): SEEqualElementsActionType

  companion object {
    @JvmStatic
    val providers: List<SEResultsEqualityProvider?>
      get() = EP_NAME.extensions.toList()

    @JvmStatic
    fun composite(providers: Collection<SEResultsEqualityProvider>): SEResultsEqualityProvider {
      return object : SEResultsEqualityProvider {
        override fun compareItems(newItem: SearchEverywhereFoundElementInfo,
                                  alreadyFoundItem: SearchEverywhereFoundElementInfo): SEEqualElementsActionType {
          return providers.asSequence()
                   .map { provider: SEResultsEqualityProvider -> provider.compareItems(newItem, alreadyFoundItem) }
                   .firstOrNull { action: SEEqualElementsActionType -> action != SEEqualElementsActionType.DO_NOTHING }
                 ?: SEEqualElementsActionType.DO_NOTHING
        }
      }
    }

    val EP_NAME: ExtensionPointName<SEResultsEqualityProvider> =
      ExtensionPointName.create("com.intellij.searchEverywhereResultsEqualityProvider")
  }
}
