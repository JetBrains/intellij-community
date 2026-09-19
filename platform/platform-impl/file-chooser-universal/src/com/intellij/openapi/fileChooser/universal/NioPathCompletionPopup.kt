// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * The popup for the [NioPathCompletion] states. It shows the loading text, the suggestions, or the no-results text.
 *
 * @param owner the component that shows the popup.
 * @param location returns the screen point of the top left corner of the popup.
 * @param onChoose runs when the user clicks a suggestion. The clicked suggestion is the [selectedValue].
 */
internal class NioPathCompletionPopup(
  private val owner: JComponent,
  private val location: () -> Point,
  private val onChoose: () -> Unit,
) {

  private var popup: JBPopup? = null

  private val listModel = CollectionListModel<NioFileChooserUtil.DirectoryEntry>()
  private val list = JBList(listModel).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = listCellRenderer("") {
      icon(value.icon ?: AllIcons.Empty)
      @NlsSafe val name = value.name
      text(name)
    }
  }
  private val listScrollPane = JBScrollPane(list)
  private val defaultLoadingText = IdeBundle.message("universal.file.chooser.label.loading")
  private val loadingLabel =
    JBLabel(defaultLoadingText, AnimatedIcon.Default.INSTANCE, SwingConstants.LEADING).apply {
      border = JBUI.Borders.empty(4, 8)
    }
  private val noResultsLabel =
    JBLabel(IdeBundle.message("file.chooser.completion.no.suggestions")).apply {
      border = JBUI.Borders.empty(4, 8)
    }

  // The single popup content. The content swaps among the loading label, the list, and the no-results label.
  private val popupPanel = JPanel(BorderLayout())

  /** True while the popup is visible. */
  val isVisible: Boolean
    get() = popup?.isVisible == true

  /** The selected suggestion, or null when nothing is selected. */
  val selectedValue: NioFileChooserUtil.DirectoryEntry?
    get() = list.selectedValue

  init {
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val index = list.locationToIndex(e.point)
        if (index >= 0) {
          list.selectedIndex = index
          onChoose()
        }
      }
    })
  }

  /** Shows the [progressText], or the default loading text when it is null. */
  fun showLoading(progressText: @Nls String?) {
    loadingLabel.text = progressText ?: defaultLoadingText
    show(loadingLabel)
  }

  /** Shows the no-results text. The suggestions go, so the next [showSuggestions] starts with no selection. */
  fun showNoResults() {
    listModel.removeAll()
    show(noResultsLabel)
  }

  /**
   * Shows the [suggestions] with their icons. The popup keeps the previous selection when the suggestions still contain it.
   * Otherwise nothing is selected.
   */
  fun showSuggestions(suggestions: List<NioFileChooserUtil.DirectoryEntry>) {
    val selected = list.selectedValue
    listModel.replaceAll(suggestions)
    val selectedIndex = suggestions.indexOf(selected)
    if (selectedIndex < 0) list.clearSelection() else list.selectedIndex = selectedIndex
    list.visibleRowCount = suggestions.size.coerceAtMost(MAX_VISIBLE_ROWS)
    show(listScrollPane)
  }

  /**
   * Moves the selection for the navigation keys. Returns true when the popup handled the key.
   * The popup opens with no selection, so the first Down selects the first suggestion.
   */
  fun moveSelection(e: KeyEvent): Boolean {
    when (e.keyCode) {
      KeyEvent.VK_DOWN -> if (list.selectedIndex < 0) list.selectedIndex = 0 else ScrollingUtil.moveDown(list, e.modifiersEx)
      KeyEvent.VK_UP -> ScrollingUtil.moveUp(list, e.modifiersEx)
      KeyEvent.VK_PAGE_DOWN -> ScrollingUtil.movePageDown(list)
      KeyEvent.VK_PAGE_UP -> ScrollingUtil.movePageUp(list)
      else -> return false
    }
    return true
  }

  /** Closes the popup. The suggestions go, so the next [showSuggestions] starts with no selection. */
  fun close() {
    popup?.cancel()
    popup = null
    listModel.removeAll()
  }

  /**
   * Puts the [component] into the single popup and shows the popup when it is not visible yet.
   * A visible popup updates in place only for the same component.
   * A new component gets a new popup, because the swapped content of a non-focusable popup can stay unpainted.
   */
  private fun show(component: JComponent) {
    val current = popup
    if (current != null && !current.isDisposed && popupPanel.components.singleOrNull() === component) {
      popupPanel.revalidate()
      popupPanel.repaint()
      current.pack(true, true)
      current.setLocation(location())
      return
    }
    current?.cancel()
    popupPanel.removeAll()
    popupPanel.add(component, BorderLayout.CENTER)
    val newPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(popupPanel, null)
      .setRequestFocus(false)
      .setFocusable(false)
      .setCancelKeyEnabled(false)
      // The popup does not shrink below the loading text, for example when the listing ends with no results.
      .setMinSize(loadingLabel.preferredSize)
      .createPopup()
    popup = newPopup
    newPopup.showInScreenCoordinates(owner, location())
  }

  private companion object {
    const val MAX_VISIBLE_ROWS = 10
  }
}
