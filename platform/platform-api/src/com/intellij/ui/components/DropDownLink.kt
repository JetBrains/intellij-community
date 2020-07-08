// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.popup.util.PopupState
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ItemEvent
import java.util.function.Consumer
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.SwingConstants

open class DropDownLink<T>(item: T, popupBuilder: (DropDownLink<T>) -> JBPopup) : ActionLink() {

  val popupState = PopupState()
  var selectedItem: T = item
    set(newItem) {
      val oldItem = field
      if (oldItem == newItem) return
      fireItemStateChanged(oldItem, ItemEvent.DESELECTED)
      field = newItem
      fireItemStateChanged(newItem, ItemEvent.SELECTED)
      updateText(newItem)
    }

  init {
    text = item.toString()
    icon = AllIcons.General.LinkDropTriangle
    iconTextGap = scale(1)
    horizontalAlignment = SwingConstants.LEADING
    horizontalTextPosition = SwingConstants.LEADING
    addActionListener {
      if (!popupState.isRecentlyHidden) {
        val popup = popupBuilder(this)
        val showPoint = Point(0, height + scale(4))
        popup.addListener(popupState)
        popup.show(RelativePoint(this, showPoint))
      }
    }
  }

  constructor(item: T, items: List<T>) : this(item, { link ->
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(items)
      .setRenderer(LinkCellRenderer(link))
      .setItemChosenCallback { link.selectedItem = it }
      .createPopup()
  })

  constructor(item: T, items: List<T>, onSelect: Consumer<T>) : this(item, items) {
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        @Suppress("UNCHECKED_CAST")
        (event.item as? T)?.let { onSelect.accept(it) }
      }
    }
  }

  override fun getSelectedObjects(): Array<Any> {
    return selectedItem?.let { arrayOf(it as Any) } ?: emptyArray()
  }

  private fun fireItemStateChanged(item: T, state: Int) {
    itemListeners.forEach { it.itemStateChanged(ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, item, state)) }
  }

  protected open fun updateText(item: T) {
    text = item.toString()
  }
}


private class LinkCellRenderer(private val link: Component) : DefaultListCellRenderer() {
  private fun coerce(size: Dimension): Dimension {
    size.width = size.width.coerceAtLeast(link.preferredSize.width)
    size.height = size.height.coerceAtLeast(scale(22))
    return size
  }

  override fun getMinimumSize() = coerce(super.getMinimumSize())
  override fun getPreferredSize() = coerce(super.getPreferredSize())
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focused: Boolean): Component {
    super.getListCellRendererComponent(list, value, index, selected, false)
    if (!selected) background = UIUtil.getLabelBackground()
    border = JBUI.Borders.empty(0, 5, 0, 10)
    return this
  }
}
