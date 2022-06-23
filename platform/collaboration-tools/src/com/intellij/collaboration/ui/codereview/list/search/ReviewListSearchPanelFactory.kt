// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAndAwaitListSubmission
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.GradientViewport
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.Nls
import java.awt.Adjustable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

abstract class ReviewListSearchPanelFactory<S : ReviewListSearchValue, VM : ReviewListSearchPanelViewModel<S>>(
  protected val vm: VM
) {

  fun create(viewScope: CoroutineScope): JComponent {
    val searchField = ReviewListSearchTextFieldFactory(vm.queryState).create(viewScope, chooseFromHistory = { point ->
      val value = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(vm.getSearchHistory().reversed())
        .setRenderer(SimpleListCellRenderer.create { label, value, _ ->
          label.text = getShortText(value)
        })
        .createPopup()
        .showAndAwaitListSubmission<S>(point)
      if (value != null) {
        vm.searchState.update { value }
      }
    })

    val filters = createFilters(viewScope)

    val filtersPanel = JPanel(HorizontalLayout(4)).apply {
      border = JBUI.Borders.emptyTop(10)
      filters.forEach { add(it, HorizontalLayout.LEFT) }
    }.let {
      ScrollPaneFactory.createScrollPane(it, true).apply {
        viewport = GradientViewport(it, JBUI.insetsRight(10), false)

        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        horizontalScrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)
      }
    }

    val searchPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.BOTTOM), JBUI.Borders.empty(8, 10, 0, 10))
      add(searchField, BorderLayout.CENTER)
      add(filtersPanel, BorderLayout.SOUTH)
    }

    return searchPanel
  }

  protected abstract fun getShortText(searchValue: S): @Nls String

  protected abstract fun createFilters(viewScope: CoroutineScope): List<JComponent>
}