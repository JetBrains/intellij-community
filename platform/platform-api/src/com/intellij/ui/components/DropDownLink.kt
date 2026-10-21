// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.popup.PopupState
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.util.function.Consumer
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.ListCellRenderer

open class DropDownLink<T>(item: T, private val popupBuilder: (DropDownLink<T>) -> JBPopup) : ActionLink() {

  @Deprecated("Do not use popupState")
  @ApiStatus.Internal
  val popupState: PopupState<JBPopup> = PopupState.forPopup()
  var selectedItem: T = item
    set(newItem) {
      val oldItem = field
      if (oldItem == newItem) return
      fireItemStateChanged(oldItem, ItemEvent.DESELECTED)
      field = newItem
      fireItemStateChanged(newItem, ItemEvent.SELECTED)
    }

  private var popupHorizontalShift = 0

  init {
    text = itemToString(item)
    setDropDownLinkIcon()
    addActionListener {
      performAction()
    }
    getInputMap(WHEN_FOCUSED)?.run {
      put(getKeyStroke(KeyEvent.VK_DOWN, 0, false), "pressed")
      put(getKeyStroke(KeyEvent.VK_DOWN, 0, true), "released")
    }
  }

  protected open fun performAction() {
    val popup = popupBuilder(this)
    popup.show(RelativePoint(this, popupPoint()))
  }

  @JvmOverloads
  constructor(item: T, items: List<T>, onChoose: Consumer<T> = Consumer { }) : this(item, { link ->
    val renderer = link.createRenderer()
    link.popupHorizontalShift = link.calcHorizontalPopupShift(renderer)
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(items)
      .setRenderer(renderer)
      // The same rule as for ComboBox: the popup is wider than the link by the shift on both sides
      .setMinSize(Dimension(link.preferredSize.width - 2 * link.popupHorizontalShift, 0))
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
  protected open fun itemToString(item: T): String = item.toString()

  protected open fun popupPoint(): Point = Point(popupHorizontalShift, height + scale(4))

  open fun createRenderer(): ListCellRenderer<in T> {
    return textListCellRenderer { it.toString() }
  }

  /**
   * Align text in popup with link text.
   */
  private fun calcHorizontalPopupShift(renderer: ListCellRenderer<*>): Int {
    if (!ExperimentalUI.isNewUI() || renderer !is ExperimentalUI.NewUIComboBoxRenderer) {
      return 0
    }
    val rowContentLeft = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get() + JBUI.CurrentTheme.Popup.Selection.innerInsets().left
    return insets.left - rowContentLeft
  }
}
