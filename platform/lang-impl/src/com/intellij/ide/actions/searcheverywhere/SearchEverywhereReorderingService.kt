// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus


private val LOG = logger<SearchEverywhereReorderingService>()

/**
 * Service that allows to customly reorder items in Search Everywhere when a new element is added
 */
@ApiStatus.Internal
interface SearchEverywhereReorderingService {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereReorderingService> = ExtensionPointName.create(
      "com.intellij.searchEverywhereReorderingService")

    /** Returns custom reordering service triggered when adding new items from contributors in Search Everywhere */
    @JvmStatic
    fun getInstance(): SearchEverywhereReorderingService? {
      val extensions = EP_NAME.extensions
      if (extensions.size > 1) {
        LOG.warn("Multiple implementations of ${SearchEverywhereReorderingService::class.java.name}. Using the first.")
      }

      return extensions.firstOrNull()?.takeIf { it.isEnabled() }
    }
  }

  /** Indicates whether custom reordering should be used */
  fun isEnabled(): Boolean

  fun isEnabledInTab(tabID: String): Boolean

  /** Performs custom reordering */
  fun reorder(tabID: String, items: MutableList<SearchEverywhereFoundElementInfo>)
}