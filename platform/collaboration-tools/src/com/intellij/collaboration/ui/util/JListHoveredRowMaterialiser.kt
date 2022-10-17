// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.util

import com.intellij.ui.ExpandedItemListCellRendererWrapper
import java.awt.Component
import java.awt.event.*
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.properties.Delegates

/**
 * Materializes a concrete component in a hovered list cell that matches the ones painted by the renderer
 */
class JListHoveredRowMaterialiser<T> private constructor(private val list: JList<T>,
                                                         private val cellRenderer: ListCellRenderer<T>) {

  private var hoveredIndex: Int by Delegates.observable(-1) { _, oldValue, newValue ->
    if (newValue != oldValue)
      materialiseRendererAt(newValue)
  }
  private var rendererComponent: Component? by Delegates.observable(null) { _, oldValue, newValue ->
    if (oldValue != null) {
      list.remove(oldValue)
    }

    if (newValue != null) {
      list.add(newValue)
      newValue.validate()
      newValue.repaint()
    }
  }

  private val listRowHoverListener = object : MouseMotionAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      val point = e.point
      val idx = list.locationToIndex(point)

      if (idx >= 0 && list.getCellBounds(idx, idx).contains(point)) {
        hoveredIndex = idx
      }
      else {
        hoveredIndex = -1
      }
    }
  }

  private val listDataListener = object : ListDataListener {
    override fun contentsChanged(e: ListDataEvent) {
      if (hoveredIndex in e.index0..e.index1) materialiseRendererAt(hoveredIndex)
    }

    override fun intervalRemoved(e: ListDataEvent) {
      if (hoveredIndex > e.index0 || hoveredIndex > e.index1) hoveredIndex = -1
    }

    override fun intervalAdded(e: ListDataEvent) {
      if (hoveredIndex > e.index0 || hoveredIndex > e.index1) hoveredIndex = -1
    }
  }

  private val listPresentationListener = object : FocusListener,
                                                  ListSelectionListener,
                                                  ComponentAdapter() {
    override fun focusLost(e: FocusEvent) = materialiseRendererAt(hoveredIndex)
    override fun focusGained(e: FocusEvent) = materialiseRendererAt(hoveredIndex)
    override fun valueChanged(e: ListSelectionEvent) = materialiseRendererAt(hoveredIndex)
    override fun componentResized(e: ComponentEvent) = materialiseRendererAt(hoveredIndex)
  }

  private fun materialiseRendererAt(index: Int) {
    if (index < 0 || index > list.model.size - 1) {
      rendererComponent = null
      return
    }

    val cellValue = list.model.getElementAt(index)
    val selected = list.isSelectedIndex(index)
    val focused = list.hasFocus() && selected

    rendererComponent = cellRenderer.getListCellRendererComponent(list, cellValue, index, selected, focused).apply {
      bounds = list.getCellBounds(index, index)
    }
  }

  /**
   * Manually redraw the cell
   */
  fun update() {
    materialiseRendererAt(hoveredIndex)
  }

  companion object {

    /**
     * [cellRenderer] should be an instance different from one in the list
     */
    fun <T> install(list: JList<T>, cellRenderer: ListCellRenderer<T>): JListHoveredRowMaterialiser<T> {
      if (list.cellRenderer === cellRenderer
          || (list.cellRenderer as? ExpandedItemListCellRendererWrapper)?.wrappee === cellRenderer)
        error("cellRenderer should be an instance different from list cell renderer")

      return JListHoveredRowMaterialiser(list, cellRenderer).also {
        with(list) {
          addMouseMotionListener(it.listRowHoverListener)
          addFocusListener(it.listPresentationListener)
          addComponentListener(it.listPresentationListener)
          addListSelectionListener(it.listPresentationListener)
          model.addListDataListener(it.listDataListener)
        }
      }
    }
  }
}