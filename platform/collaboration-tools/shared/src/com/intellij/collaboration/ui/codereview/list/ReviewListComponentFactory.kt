// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.collaboration.ui.util.JListHoveredRowMaterialiser
import com.intellij.openapi.application.UI
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ReviewListComponentFactory<T>(private val listModel: ListModel<T>) {
  fun create(
    itemPresenter: (T) -> ReviewListItemPresentation,
  ): JBList<T> {
    return createList(itemPresenter).also {
      JListHoveredRowMaterialiser.install(it, ReviewListCellRenderer(itemPresenter))
    }.apply {
      launchOnShow("ReviewListGlassPane") {
        val cs = this
        val list = this@apply
        val glassPane = IdeGlassPaneUtil.find(list)
        val mouseListener = object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) {
            if (e == null) return
            val pointInList = SwingUtilities.convertPoint(e.component, e.point, list)
            val index = list.locationToIndex(pointInList)
            if (index < 0) return
            val cellBounds = list.getCellBounds(index, index)
            if (cellBounds != null && cellBounds.contains(pointInList)) {
              list.selectedIndex = index
            }
          }
        }
        glassPane.addMouseListener(mouseListener, cs)
      }
    }
  }

  @ApiStatus.Internal
  fun create(
    itemPresenter: (T) -> ReviewListItemPresentation,
    options: ReviewListCellUiOptions,
  ): JBList<T> {
    return createList(itemPresenter, options).also {
      JListHoveredRowMaterialiser.install(it, ReviewListCellRenderer(itemPresenter, options)).also {
        it.resetCellBoundsOnHover = options.bordered
      }
    }
  }

  @ApiStatus.Internal
  private fun createList(
    itemPresenter: (T) -> ReviewListItemPresentation,
    options: ReviewListCellUiOptions = ReviewListCellUiOptions(),
  ): JBList<T> {
    val listCellRenderer = ReviewListCellRenderer(itemPresenter, options)
    return JBList(listModel).apply {
      emptyText.clear()
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = listCellRenderer

      setExpandableItemsEnabled(false)
    }.also {
      ScrollingUtil.installActions(it)
      ListUiUtil.Selection.installSelectionOnFocus(it)
      ListUiUtil.Selection.installSelectionOnRightClick(it)
      UIUtil.addNotInHierarchyComponents(it, listOf(listCellRenderer))
    }
  }
}


object ReviewListUtil {
  fun wrapWithLazyVerticalScroll(cs: CoroutineScope, list: JList<*>, requestor: () -> Unit): JScrollPane =
    ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

      val model = verticalScrollBar.model
      val listener = object : BoundedRangeModelThresholdListener(model, 0.7f) {
        override fun onThresholdReached() {
          requestor()
        }
      }
      model.addChangeListener(listener)

      list.model.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent) {
          cs.launch(Dispatchers.UI) {
            // yield to let list resize itself
            yield()
            checkScroll()
          }
        }

        private fun checkScroll() {
          if (list.isShowing) {
            listener.stateChanged(ChangeEvent(list))
          }
        }

        override fun intervalRemoved(e: ListDataEvent) = Unit
        override fun contentsChanged(e: ListDataEvent) = Unit
      })
    }
}