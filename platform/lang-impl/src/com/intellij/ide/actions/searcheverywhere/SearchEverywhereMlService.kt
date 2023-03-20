// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import javax.swing.ListCellRenderer

@ApiStatus.Internal
abstract class SearchEverywhereMlService {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereMlService> = ExtensionPointName.create("com.intellij.searchEverywhereMlService")

    /**
     * Returns an instance of the service if Machine Learning in Search Everywhere is enabled (see [isEnabled]), null otherwise.
     */
    @JvmStatic
    fun getInstance(): SearchEverywhereMlService? {
      val extensions = EP_NAME.extensions
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
   * (see [com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlExperiment.isAllowed])
   */
  abstract fun isEnabled(): Boolean

  abstract fun onSessionStarted(project: Project?, mixedListInfo: SearchEverywhereMixedListInfo)

  @Contract("_, _, _ -> new")
  abstract fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                      element: Any,
                                      priority: Int): SearchEverywhereFoundElementInfo

  abstract fun onSearchRestart(project: Project?, tabId: String, reason: SearchRestartReason,
                               keysTyped: Int, backspacesTyped: Int, searchQuery: String,
                               previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                               searchScope: ScopeDescriptor?, isSearchEverywhere: Boolean)

  abstract fun onItemSelected(project: Project?, indexes: IntArray, selectedItems: List<Any>, closePopup: Boolean,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>)

  abstract fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>)

  abstract fun notifySearchResultsUpdated()

  abstract fun onDialogClose()

  abstract fun wrapRenderer(renderer: ListCellRenderer<Any>, listModel: SearchListModel): ListCellRenderer<Any>

  abstract fun buildListener(listModel: SearchListModel, resultsList: JBList<Any>, selectionTracker: SEListSelectionTracker): SearchListener?
}