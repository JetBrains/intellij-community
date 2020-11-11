// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupState
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.util.function.Consumer
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.KeyStroke.getKeyStroke

open class DropDownLink<T>(item: T, popupBuilder: (DropDownLink<T>) -> JBPopup) : ActionLink() {

  val popupState = PopupState.forPopup()
  var selectedItem: T = item
    set(newItem) {
      val oldItem = field
      if (oldItem == newItem) return
      fireItemStateChanged(oldItem, ItemEvent.DESELECTED)
      field = newItem
      fireItemStateChanged(newItem, ItemEvent.SELECTED)
    }

  init {
    text = itemToString(item)
    setDropDownLinkIcon()
    addActionListener {
      if (!popupState.isRecentlyHidden) {
        val popup = popupBuilder(this)
        popupState.prepareToShow(popup)
        popup.show(RelativePoint(this, popupPoint()))
      }
    }
    getInputMap(WHEN_FOCUSED)?.run {
      put(getKeyStroke(KeyEvent.VK_DOWN, 0, false), "pressed")
      put(getKeyStroke(KeyEvent.VK_DOWN, 0, true), "released")
    }
  }

  @JvmOverloads
  constructor(item: T, items: List<T>, onChoose: Consumer<T> = Consumer { }) : this(item, { link ->
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(items)
      .setRenderer(LinkCellRenderer(link))
      .setItemChosenCallback {
        onChoose.accept(it)
        link.selectedItem = it
      }
      .createPopup()
  })

  constructor(item: T, items: List<T>, onSelect: Consumer<T>, updateText: Boolean) : this(item, items) {
    addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        @Suppress("UNCHECKED_CAST")
        (event.item as? T)?.let {
          onSelect.accept(it)
          if (updateText) text = itemToString(it)
        }
      }
    }
  }

  override fun getSelectedObjects(): Array<Any> {
    return selectedItem?.let { arrayOf(it as Any) } ?: emptyArray()
  }

  private fun fireItemStateChanged(item: T, state: Int) {
    itemListeners.forEach { it.itemStateChanged(ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, item, state)) }
  }

  @Nls
  protected open fun itemToString(item: T) = item.toString()

  protected open fun popupPoint() = Point(0, height + scale(4))
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
