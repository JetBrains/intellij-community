// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.util.popup.showAndAwaitListSubmission
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.GradientViewport
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Adjustable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

abstract class ReviewListSearchPanelFactory<S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>, VM : ReviewListSearchPanelViewModel<S, Q>>(
  protected val vm: VM
) {

  fun create(viewScope: CoroutineScope): JComponent =
    create(viewScope, null)

  @Internal
  fun create(viewScope: CoroutineScope, quickFilterListener: ((Q) -> Unit)?): JComponent {
    val searchField = ReviewListSearchFiltersTextFieldFactory
      .create(
        vm.queryState,
        submitSearchText = { text ->
          vm.queryState.update { text }
        },
        chooseFromHistory = { point ->
          val value = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(vm.getSearchHistory().reversed())
            .setRenderer(SimpleListCellRenderer.create { label, value, _ ->
              label.text = getShortText(value)
            })
            .createPopup()
            .showAndAwaitListSubmission<S>(point, ShowDirection.BELOW)
          if (value != null) {
            vm.searchState.update { value }
          }
        })

    val filters = createFilters(viewScope)

    val filtersPanel = HorizontalListPanel(4).apply {
      filters.forEach { add(it) }
    }.let {
      ScrollPaneFactory.createScrollPane(it, true).apply {
        viewport = GradientViewport(it, JBUI.insets(0, 10), false)

        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        horizontalScrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)

        ClientProperty.put(this, JBScrollPane.FORCE_HORIZONTAL_SCROLL, true)
      }
    }

    val quickFilterButton = QuickFilterButtonFactory.create(
      vm.searchState,
      vm.quickFilters,
      quickFilterListener,
      { it.getQuickFilterTitle() },
      { newQuery -> vm.searchState.update { newQuery } },
      { vm.searchState.update { vm.emptySearch } }
    )

    val filterPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyTop(10)
      isOpaque = false
      add(quickFilterButton, BorderLayout.WEST)
      add(filtersPanel, BorderLayout.CENTER)
    }

    val searchPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(8, 10, 0, 10)
      add(searchField, BorderLayout.CENTER)
      add(filterPanel, BorderLayout.SOUTH)
    }

    return searchPanel
  }

  protected abstract fun getShortText(searchValue: S): @Nls String

  protected abstract fun createFilters(viewScope: CoroutineScope): List<JComponent>

  protected abstract fun Q.getQuickFilterTitle(): @Nls String
}