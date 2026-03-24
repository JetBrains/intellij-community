// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Internal
interface SearchEverywhereMlService {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereMlService> = ExtensionPointName.create("com.intellij.searchEverywhereMlService")

    /**
     * Returns an instance of the service if Machine Learning in Search Everywhere is enabled (see [isEnabled]), null otherwise.
     */
    @JvmStatic
    fun getInstance(): SearchEverywhereMlService? {
      val extensions = EP_NAME.extensionList
      if (extensions.size > 1) {
        val logger = Logger.getInstance(SearchEverywhereMlService::class.java)
        logger.warn("Multiple implementations of ${SearchEverywhereMlService::class.java.name}. Using the first.")
      }

      return extensions.firstOrNull()?.takeIf { it.isEnabled() }
    }
  }

  /**
   * Indicates whether machine learning in Search Everywhere is enabled.
   * This method can return false if ML-ranking is disabled and no experiments are allowed
   * (see [com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.isAllowed])
   */
  fun isEnabled(): Boolean

  fun onSessionStarted(project: Project?, tabId: String, mixedListInfo: SearchEverywhereMixedListInfo)

  @Contract("_, _, _ -> new")
  fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                             element: Any,
                             priority: Int,
                             correction: SearchEverywhereSpellCheckResult): SearchEverywhereFoundElementInfo

  fun onStateStarted(tabId: String,
                     reason: SearchRestartReason,
                     searchQuery: String,
                     searchScope: ScopeDescriptor?,
                     isSearchEverywhere: Boolean)

  fun onStateFinished(results: List<SearchEverywhereFoundElementInfo>)

  fun onItemSelected(tabId: String,
                     indexes: IntArray, selectedItems: List<Any>,
                     searchResults: List<SearchEverywhereFoundElementInfo>,
                     query: String)

  fun onSessionFinished()

  fun notifySearchResultsUpdated()

  fun onDialogClose()

  fun getExperimentVersion(): Int

  fun getExperimentGroup(): Int
}
