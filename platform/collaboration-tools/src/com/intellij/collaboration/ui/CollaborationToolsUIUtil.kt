// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ComponentUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent

object CollaborationToolsUIUtil {

  /**
   * Connects [searchTextField] to a [list] to be used as a filter
   */
  fun <T> attachSearch(list: JList<T>, searchTextField: SearchTextField, searchBy: (T) -> String) {
    val speedSearch = SpeedSearch(false)
    val filteringListModel = NameFilteringListModel<T>(list.model, searchBy, speedSearch::shouldBeShowing, speedSearch.filter::orEmpty)
    list.model = filteringListModel

    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = speedSearch.updatePattern(searchTextField.text)
    })

    speedSearch.addChangeListener {
      val prevSelection = list.selectedValue // save to restore the selection on filter drop
      filteringListModel.refilter()
      if (filteringListModel.size > 0) {
        val fullMatchIndex = if (speedSearch.isHoldingFilter) filteringListModel.closestMatchIndex
        else filteringListModel.getElementIndex(prevSelection)
        if (fullMatchIndex != -1) {
          list.selectedIndex = fullMatchIndex
        }

        if (filteringListModel.size <= list.selectedIndex || !filteringListModel.contains(list.selectedValue)) {
          list.selectedIndex = 0
        }
      }
    }

    ScrollingUtil.installActions(list)
    ScrollingUtil.installActions(list, searchTextField.textEditor)
  }

  /**
   * Adds actions to transfer focus by tab/shift-tab key for given [component].
   *
   * May be helpful for overwriting tab symbol input for text fields
   */
  fun registerFocusActions(component: JComponent) {
    component.registerKeyboardAction({ component.transferFocus() },
                                     KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                                     JComponent.WHEN_FOCUSED)
    component.registerKeyboardAction({ component.transferFocusBackward() },
                                     KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                                     JComponent.WHEN_FOCUSED)
  }

  /**
   * Add [listener] that will be invoked on each "UI" property change
   */
  fun <T : JComponent> overrideUIDependentProperty(component: T, listener: T.() -> Unit) {
    component.addPropertyChangeListener("UI", PropertyChangeListener {
      listener.invoke(component)
    })
    listener.invoke(component)
  }

  /**
   * Makes the button blue like a default button in dialogs
   */
  fun JButton.defaultButton(): JButton {
    ComponentUtil.putClientProperty(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    return this
  }

  /**
   * Removes http(s) protocol and trailing slash from given [url]
   */
  @Suppress("HttpUrlsUsage")
  @NlsSafe
  fun cleanupUrl(@NlsSafe url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removeSuffix("/")
}