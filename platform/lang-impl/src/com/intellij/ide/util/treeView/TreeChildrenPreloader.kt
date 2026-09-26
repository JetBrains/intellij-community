@file:ApiStatus.Experimental
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTree
import kotlin.time.Duration.Companion.milliseconds



@get:ApiStatus.Experimental
@set:ApiStatus.Experimental
var JTree.isChildrenPreloadingEnabled: Boolean
  get() = ClientProperty.get(this, CHILDREN_PRELOADING_JOB_KEY) != null
  set(value) {
    val existingJob = ClientProperty.get(this, CHILDREN_PRELOADING_JOB_KEY)
    if (existingJob == null && value) {
      ClientProperty.put(this, CHILDREN_PRELOADING_JOB_KEY, enableChildrenPreloading(this))
    }
    else if (existingJob != null && !value) {
      existingJob.cancel()
      ClientProperty.put(this, CHILDREN_PRELOADING_JOB_KEY, null)
    }
  }

private val CHILDREN_PRELOADING_JOB_KEY = Key.create<Job>("TREE_CHILDREN_PRELOADING_JOB")

private fun enableChildrenPreloading(tree: JTree): Job {
  return tree.launchOnShow("TreeChildrenPreloader") {
    TreeChildrenPreloader(tree).run()
  }
}

@OptIn(FlowPreview::class)
private class TreeChildrenPreloader(private val tree: JTree) {

  private val uiListeners = UiListeners(tree) {
    updateVisibleRange()
  }
  private val visibleRangeFlow = MutableStateFlow(getVisibleRange())

  private fun updateVisibleRange() {
    visibleRangeFlow.value = getVisibleRange()
  }

  private fun getVisibleRange(): IntRange {
    // If the tree has a cached presentation at the moment, it's impossible to preload any children
    // because their parents are not yet loaded.
    if (tree is CachedTreePresentationSupport && tree.cachedPresentation != null) return IntRange.EMPTY
    val visibleRect = tree.visibleRect
    val topRow = tree.getClosestRowForLocation(visibleRect.x,  visibleRect.y)
    val bottomRow = tree.getClosestRowForLocation(visibleRect.x,  visibleRect.y + visibleRect.height)
    return topRow..bottomRow
  }

  suspend fun run() = coroutineScope {
    try {
      uiListeners.install()
      visibleRangeFlow.debounce(50.milliseconds)
        .collectLatest { visibleRange ->
          preloadChildrenForRowRange(visibleRange)
        }
    }
    finally {
      uiListeners.uninstall()
    }
  }

  private fun preloadChildrenForRowRange(visibleRange: IntRange) {
    val model = tree.model ?: return
    for (row in visibleRange) {
      val path = tree.getPathForRow(row) ?: continue
      val value = path.lastPathComponent
      if (!model.isLeaf(value)) {
        model.getChildCount(value)
      }
    }
  }

  private class UiListeners(private val tree: JTree, onUiChange: () -> Unit) {
    private val scrollPane: JScrollPane? = ComponentUtil.getParentOfType(JScrollPane::class.java, tree)
    private var scrollBar: JScrollBar? = scrollPane?.verticalScrollBar

    private val cachedPresentationPropertyListener = PropertyChangeListener {
      onUiChange()
    }

    private val resizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        onUiChange()
      }
    }

    private val scrollBarAdjustmentListener = AdjustmentListener {
      onUiChange()
    }

    private val scrollBarPropertyListener = PropertyChangeListener {
      scrollBar?.removeAdjustmentListener(scrollBarAdjustmentListener)
      scrollBar = scrollPane?.verticalScrollBar
      scrollBar?.addAdjustmentListener(scrollBarAdjustmentListener)
    }

    fun install() {
      tree.addPropertyChangeListener(CACHED_TREE_PRESENTATION_PROPERTY, cachedPresentationPropertyListener)
      scrollPane?.addComponentListener(resizeListener)
      scrollBar?.addAdjustmentListener(scrollBarAdjustmentListener)
      scrollPane?.addPropertyChangeListener("verticalScrollBar", scrollBarPropertyListener)
    }

    fun uninstall() {
      scrollPane?.removePropertyChangeListener("verticalScrollBar", scrollBarPropertyListener)
      scrollBar?.removeAdjustmentListener(scrollBarAdjustmentListener)
      scrollPane?.removeComponentListener(resizeListener)
      tree.removePropertyChangeListener(CACHED_TREE_PRESENTATION_PROPERTY, cachedPresentationPropertyListener)
    }
  }
}
