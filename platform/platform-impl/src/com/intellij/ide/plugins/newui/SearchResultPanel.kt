// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.newui.PluginLogo.endBatchMode
import com.intellij.ide.plugins.newui.PluginLogo.startBatchMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.ScrollPaneConstants

@ApiStatus.Internal
abstract class SearchResultPanel(
  protected val coroutineScope: CoroutineScope,
  @JvmField val controller: SearchPopupController,
  @JvmField protected val myPanel: PluginsGroupComponentWithProgress,
  private val isMarketplace: Boolean,
) {
  private var myVerticalScrollBar: JScrollBar? = null
  var group: PluginsGroup
    private set
  var query: String = ""
    private set
  private var myQueryJob: Job? = null
  private var isLoading = false
  private var myAnnounceSearchResultsAlarm: SingleAlarm? = null

  init {
    myPanel.getAccessibleContext().setAccessibleName(IdeBundle.message("title.search.results"))
    group = PluginsGroup(
      IdeBundle.message("title.search.results"),
      if (isMarketplace) PluginsGroupType.SEARCH else PluginsGroupType.SEARCH_INSTALLED
    )
    setupEmptyText()
    loading(false)
  }

  val panel: PluginsGroupComponent
    get() = myPanel

  fun createScrollPane(): JComponent {
    val pane = JBScrollPane(myPanel)
    pane.setBorder(JBUI.Borders.empty())
    myVerticalScrollBar = pane.getVerticalScrollBar()
    return pane
  }

  fun createVScrollPane(): JComponent {
    val pane = createScrollPane() as JBScrollPane
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    return pane
  }

  protected open fun setupEmptyText() {
    myPanel.getEmptyText().setText(IdeBundle.message("empty.text.nothing.found"))
  }

  val isQueryEmpty: Boolean
    get() = query.isEmpty()

  fun setEmptyQuery() {
    query = ""
  }

  @RequiresEdt
  open fun setQuery(query: String) {
    assert(EDT.isCurrentThreadEdt())
    if (query == this.query) {
      return
    }

    if (myQueryJob != null) {
      myQueryJob?.cancel()
      loading(false)
    }

    removeGroup()
    this.query = query
    setupEmptyText()

    if (!isQueryEmpty) {
      handleQuery(query)
    }
  }

  private fun handleQuery(query: String) {
    loading(true)

    val group = this.group

    myQueryJob = coroutineScope.launch(Dispatchers.IO) {
      handleQuery(query, group)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        loading(false)
      }
    }
  }

  protected suspend fun updatePanel() {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      ensureActive()

      loading(false)

      if (!group.getDescriptors().isEmpty()) {
        group.titleWithCount()
        try {
          startBatchMode()
          myPanel.addLazyGroup(group, myVerticalScrollBar!!, 100, Runnable { fullRepaint() })
        }
        finally {
          endBatchMode()
        }
      }

      announceSearchResultsWithDelay()
      myPanel.initialSelection(false)
      fullRepaint()
    }
  }

  protected abstract suspend fun handleQuery(query: String, result: PluginsGroup)

  private fun loading(start: Boolean) {
    val panel = myPanel
    if (start) {
      isLoading = true
      panel.showLoadingIcon()
    }
    else {
      isLoading = false
      panel.hideLoadingIcon()
    }
  }

  fun dispose() {
    myPanel.dispose()
    if (myAnnounceSearchResultsAlarm != null) {
      Disposer.dispose(myAnnounceSearchResultsAlarm!!)
    }
  }

  fun removeGroup() {
    if (group.ui != null) {
      myPanel.removeGroup(this.group)
      fullRepaint()
    }
    this.group = PluginsGroup(
      IdeBundle.message("title.search.results"),
      if (isMarketplace) PluginsGroupType.SEARCH else PluginsGroupType.SEARCH_INSTALLED
    )
  }

  fun fullRepaint() {
    myPanel.doLayout()
    myPanel.revalidate()
    myPanel.repaint()
  }

  private fun announceSearchResultsWithDelay() {
    if (AccessibleAnnouncerUtil.isAnnouncingAvailable()) {
      if (myAnnounceSearchResultsAlarm == null) {
        myAnnounceSearchResultsAlarm =
          SingleAlarm(
            Runnable { this.announceSearchResults() },
            250,
            null,
            Alarm.ThreadToUse.SWING_THREAD,
            ModalityState.stateForComponent(myPanel)
          )
      }

      myAnnounceSearchResultsAlarm!!.cancelAndRequest()
    }
  }

  private fun announceSearchResults() {
    if (myPanel.isShowing() && !isLoading) {
      val pluginsTabName = IdeBundle.message(if (isMarketplace) "plugin.manager.tab.marketplace" else "plugin.manager.tab.installed")
      val message = IdeBundle.message(
        "plugins.configurable.search.result.0.plugins.found.in.1",
        group.getDescriptors().size, pluginsTabName
      )
      AccessibleAnnouncerUtil.announce(myPanel, message, false)
    }
  }
}