// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

abstract class SearchEverywhereMlService {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereMlService> = ExtensionPointName.create("com.intellij.searchEverywhereMlService")

    @JvmStatic
    fun getInstance(): SearchEverywhereMlService? {
      val extensions = EP_NAME.extensions
      if (extensions.size > 1) {
        val logger = Logger.getInstance(SearchEverywhereMlService::class.java)
        logger.warn("Multiple implementations of ${SearchEverywhereMlService::class.java.name}. Using the first.")
      }

      return extensions.firstOrNull()
    }
  }

  abstract fun shouldOrderByMl(tabId: String): Boolean

  abstract fun getMlWeight(contributor: SearchEverywhereContributor<*>, element: Any, matchingDegree: Int): Double

  abstract fun onSessionStarted(project: Project?)

  abstract fun onSearchRestart(project: Project?, tabId: String, reason: SearchRestartReason,
                               keysTyped: Int, backspacesTyped: Int, searchQuery: String,
                               previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>)

  abstract fun onItemSelected(project: Project?, indexes: IntArray, selectedItems: List<Any>, closePopup: Boolean,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>)

  abstract fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>)

  abstract fun notifySearchResultsUpdated()

  abstract fun onDialogClose()
}