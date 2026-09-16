// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.newui.SearchQueryParser.Companion.getTagQuery
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.SingleEdtTaskScheduler.Companion.createSingleEdtTaskScheduler
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
abstract class PluginsTab @RequiresEdt(generateAssertion = false /* IJPL-115548 */) constructor(
  searchTextFieldQueryDebouncePeriodMs: Long
) {
  private val searchUpdateAlarm = createSingleEdtTaskScheduler()

  protected val searchTextField: PluginSearchTextField = createSearchTextField(searchTextFieldQueryDebouncePeriodMs)
  private val defaultOrSearchResultsViewPanel: MultiPanel = createDefaultOrSearchResultsViewPanel()
  @JvmField val searchListener: LinkListener<Any> = createSearchListener()
  protected val selectionListener: Consumer<PluginsGroupComponent?> = createSelectionListener()

  protected abstract val detailsPage: PluginDetailsPageComponent
  protected abstract val searchPanel: SearchResultPanel

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun createPanel(): JComponent {
    val listPanel = JPanel(BorderLayout())
    listPanel.setBorder(CustomLineBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, JBUI.insetsTop(1)))
    listPanel.add(searchTextField, BorderLayout.NORTH)
    listPanel.add(defaultOrSearchResultsViewPanel)

    val splitter: OnePixelSplitter = object : OnePixelSplitter(false, 0.45f) {
      override fun createDivider(): Divider {
        val divider = super.createDivider()
        divider.setBackground(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR)
        return divider
      }
    }
    splitter.setFirstComponent(listPanel)
    splitter.setSecondComponent(detailsPage)

    defaultOrSearchResultsViewPanel.select(DEFAULT_PANEL, true)

    return splitter
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  protected abstract fun createPluginsPanel(): JComponent

  protected abstract fun updateMainSelection(selectionListener: Consumer<in PluginsGroupComponent?>)

  var searchQuery: String?
    get() {
      if (searchPanel.isQueryEmpty) {
        return null
      }
      val query = searchPanel.query
      return query.ifEmpty { null }
    }
    set(query) {
      searchTextField.setTextIgnoreEvents(query)
      if (query == null) {
        hideSearchPanel()
      }
      else {
        showSearchPanel(query)
      }
    }

  fun showSearchPanel(query: String) {
    if (searchPanel.isQueryEmpty) {
      defaultOrSearchResultsViewPanel.select(SEARCH_PANEL, true)
      detailsPage.showPlugin(null)
    }
    searchPanel.setQuery(query)
    searchTextField.addCurrentTextToHistory()
  }

  open fun hideSearchPanel() {
    if (!searchPanel.isQueryEmpty) {
      onSearchReset()
      defaultOrSearchResultsViewPanel.select(DEFAULT_PANEL, true)
      searchPanel.setQuery("")
      updateMainSelection(selectionListener)
    }
    searchPanel.controller.hidePopup()
  }

  protected abstract fun onSearchReset()

  private fun showSearchPopup() {
    if (searchTextField.text.isNullOrBlank()) {
      searchPanel.controller.showAttributesPopup(null, 0)
    }
    else {
      searchPanel.controller.handleShowPopup()
    }
  }

  fun clearSearchPanel(query: String) {
    hideSearchPanel()
    searchTextField.setTextIgnoreEvents(query)
  }

  open fun dispose() {
    detailsPage.detach()
    searchUpdateAlarm.dispose()
    searchTextField.disposeUIResources()
  }

  private fun createSearchTextField(searchTextFieldQueryDebouncePeriodMs: Long): PluginSearchTextField {
    val searchTextField = object : PluginSearchTextField() {
      override fun preprocessEventForTextField(event: KeyEvent): Boolean {
        val keyCode = event.getKeyCode()
        val id = event.getID()

        if (keyCode == KeyEvent.VK_ENTER || event.getKeyChar() == '\n') {
          if (id == KeyEvent.KEY_PRESSED && !searchPanel.controller.handleEnter(event)) {
            val text = getText()
            if (!text.isEmpty()) {
              searchPanel.controller.hidePopup()
              showSearchPanel(text)
            }
          }
          return true
        }
        if ((keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) && id == KeyEvent.KEY_PRESSED &&
            searchPanel.controller.handleUpDown(event)
        ) {
          return true
        }
        return super.preprocessEventForTextField(event)
      }

      override fun toClearTextOnEscape(): Boolean {
        object : AnAction() {
          init {
            isEnabledInModalContext = true
          }

          override fun update(e: AnActionEvent) {
            e.presentation.setEnabled(!text.isEmpty())
          }

          override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

          override fun actionPerformed(e: AnActionEvent) {
            if (searchPanel.controller.isPopupShow) {
              searchPanel.controller.hidePopup()
            }
            else {
              text = ""
            }
          }
        }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, this)
        return false
      }

      override fun onFieldCleared() {
        hideSearchPanel()
      }

      override fun showCompletionPopup() {
        if (!searchPanel.controller.isPopupShow) {
          showSearchPopup()
        }
      }
    }

    searchTextField.textEditor.document.addDocumentListener(createPerformSearchOnInputListener(searchTextFieldQueryDebouncePeriodMs))

    val editor = searchTextField.textEditor
    editor.putClientProperty("JTextField.Search.Gap", scale(6))
    editor.putClientProperty("JTextField.Search.GapEmptyText", scale(-1))
    editor.putClientProperty(
      TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
      Predicate { field: JBTextField? -> field!!.getText().isEmpty() })
    editor.setOpaque(true)
    editor.setBackground(PluginManagerConfigurable.SEARCH_BG_COLOR)
    editor.getAccessibleContext().setAccessibleName(IdeBundle.message("plugin.manager.search.accessible.name"))

    val text = IdeBundle.message("plugin.manager.options.command")

    val emptyText = searchTextField.textEditor.emptyText
    emptyText.appendText(text, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ListPluginComponent.GRAY_COLOR))

    return searchTextField
  }

  private fun createPerformSearchOnInputListener(searchTextFieldQueryDebouncePeriodMs: Long): DocumentAdapter = object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      if (!searchTextField.isSkipDocumentEvents) {
        searchUpdateAlarm.cancelAndRequest(
          searchTextFieldQueryDebouncePeriodMs,
          ModalityState.stateForComponent(searchTextField),
          Runnable { this.searchOnTheFly() })
      }
    }

    fun searchOnTheFly() {
      val text = searchTextField.text
      if (text.isNullOrBlank()) {
        hideSearchPanel()
      }
      else {
        searchPanel.controller.handleShowPopup()
      }
    }
  }

  private fun createDefaultOrSearchResultsViewPanel(): MultiPanel {
    return object : MultiPanel() {
      override fun addNotify() {
        super.addNotify()
        EventHandler.addGlobalAction(
          searchTextField, CustomShortcutSet(KeyStroke.getKeyStroke("meta alt F")),
          Runnable {
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable {
              IdeFocusManager.getGlobalInstance().requestFocus(searchTextField, true)
            })
          })
      }

      override fun create(key: Int): JComponent? {
        if (key == DEFAULT_PANEL) {
          return createPluginsPanel()
        }
        if (key == SEARCH_PANEL) {
          return searchPanel.createVScrollPane()
        }
        return super.create(key)
      }
    }
  }

  private fun createSearchListener(): LinkListener<in Any> = LinkListener { _: LinkLabel<Any>?, data: Any ->
    val query: String?
    when (data) {
      is String -> query = data
      is TagComponent -> query = getTagQuery(data.text)
      else -> return@LinkListener
    }

    searchTextField.setTextIgnoreEvents(query)
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable {
      IdeFocusManager.getGlobalInstance().requestFocus(searchTextField, true)
    })
    searchPanel.setEmptyQuery()
    showSearchPanel(query)
  }

  private fun createSelectionListener(): Consumer<PluginsGroupComponent?> = Consumer { panel: PluginsGroupComponent? ->
    val key: Int = if (searchPanel.panel === panel) SEARCH_PANEL else DEFAULT_PANEL
    if (defaultOrSearchResultsViewPanel.key == key) {
      detailsPage.showPlugins(panel!!.selection)
    }
  }

  companion object {
    private const val DEFAULT_PANEL = 0
    private const val SEARCH_PANEL = 1
  }
}
