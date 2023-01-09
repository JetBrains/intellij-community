// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.application.subscribe
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ClientProperty
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.content.Content
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates

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
   * Add [listener] that will be invoked on each UI update
   */
  fun <T : JComponent> overrideUIDependentProperty(component: T, listener: T.() -> Unit) {
    UiNotifyConnector(component, object : Activatable {
      private var listenerDisposable: Disposable? by Delegates.observable(null) { _, oldValue, _ ->
        oldValue?.also { Disposer.dispose(it) }
      }

      override fun showNotify() {
        val disposable = Disposer.newDisposable()
        LafManagerListener.TOPIC.subscribe(disposable, LafManagerListener { listener(component) })
        listenerDisposable = disposable
      }

      override fun hideNotify() {
        listenerDisposable = null
      }
    })
    listener(component)
  }

  /**
   * Makes the button blue like a default button in dialogs
   */
  fun JButton.defaultButton(): JButton = apply {
    isDefault = true
  }

  /**
   * Makes the button blue like a default button in dialogs
   */
  var JButton.isDefault: Boolean
    get() = ClientProperty.isTrue(this, DarculaButtonUI.DEFAULT_STYLE_KEY)
    set(value) {
      if (value) {
        ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
      }
      else {
        ClientProperty.remove(this, DarculaButtonUI.DEFAULT_STYLE_KEY)
      }
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

  /**
   * Checks if focus is somewhere down the hierarchy from [component]
   */
  fun isFocusParent(component: JComponent): Boolean {
    val focusOwner = IdeFocusManager.findInstanceByComponent(component).focusOwner ?: return false
    return SwingUtilities.isDescendingFrom(focusOwner, component)
  }

  /**
   * Finds the proper focus target for [panel] and set focus to it
   */
  fun focusPanel(panel: JComponent) {
    val focusManager = IdeFocusManager.findInstanceByComponent(panel)
    val toFocus = focusManager.getFocusTargetFor(panel) ?: return
    focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
  }

  fun setComponentPreservingFocus(content: Content, component: JComponent) {
    val focused = isFocusParent(content.component)
    content.component = component
    if (focused) {
      focusPanel(content.component)
    }
  }

  fun getFocusBorderInset(): Int {
    val bw: Int = if (UIUtil.isUnderDefaultMacTheme()) 3 else DarculaUIUtil.BW.unscaled.toInt()
    val lw: Int = if (UIUtil.isUnderDefaultMacTheme()) 0 else DarculaUIUtil.LW.unscaled.toInt()
    return bw + lw
  }

  fun wrapWithLimitedSize(component: JComponent, maxWidth: Int? = null, maxHeight: Int? = null): JComponent {
    val layout = SizeRestrictedSingleComponentLayout().apply {
      this.maxWidth = maxWidth
      this.maxHeight = maxHeight
    }
    return JPanel(layout).apply {
      name = "Size limit wrapper"
      isOpaque = false
      add(component)
    }
  }
}

@Suppress("FunctionName")
fun VerticalListPanel(gap: Int = 0): JPanel =
  JPanel(ListLayout.vertical(gap)).apply {
    isOpaque = false
  }

@Suppress("FunctionName")
fun HorizontalListPanel(gap: Int = 0): JPanel =
  JPanel(ListLayout.horizontal(gap)).apply {
    isOpaque = false
  }

internal fun <E> ListModel<E>.findIndex(item: E): Int {
  for (i in 0 until size) {
    if (getElementAt(i) == item) return i
  }
  return -1
}

internal val <E> ListModel<E>.items
  get() = Iterable {
    object : Iterator<E> {
      private var idx = -1

      override fun hasNext(): Boolean = idx < size - 1

      override fun next(): E {
        idx++
        return getElementAt(idx)
      }
    }
  }

fun ComboBoxModel<*>.selectFirst() {
  val size = size
  if (size == 0) {
    return
  }
  val first = getElementAt(0)
  selectedItem = first
}