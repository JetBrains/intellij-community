@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.treeStructure.BgtAwareTreeModel
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.time.Duration.Companion.milliseconds



/**
 * When `true`, the tree preloads the children of the visible nodes and of the top-level nodes while it is showing.
 *
 * The top-level nodes are the children of the root.
 * The tree preloads their children also when they are not visible.
 */
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

  private val uiListeners = UiListeners(
    tree,
    onVisibleRangeChange = { updateVisibleRange() },
    onTopLevelNodesChange = { topLevelNodesChanges.update { it + 1 } },
  )
  private val visibleRangeFlow = MutableStateFlow(getVisibleRange())
  private val topLevelNodesChanges = MutableStateFlow(0L)

  private fun updateVisibleRange() {
    visibleRangeFlow.value = getVisibleRange()
  }

  private fun getVisibleRange(): IntRange {
    if (hasCachedPresentation()) return IntRange.EMPTY
    val visibleRect = tree.visibleRect
    val topRow = tree.getClosestRowForLocation(visibleRect.x,  visibleRect.y)
    val bottomRow = tree.getClosestRowForLocation(visibleRect.x,  visibleRect.y + visibleRect.height)
    return topRow..bottomRow
  }

  // If the tree has a cached presentation at the moment, it's impossible to preload any children
  // because their parents are not yet loaded.
  private fun hasCachedPresentation(): Boolean = tree is CachedTreePresentationSupport && tree.cachedPresentation != null

  suspend fun run() = coroutineScope {
    try {
      uiListeners.install()
      launch {
        topLevelNodesChanges.debounce(50.milliseconds)
          .collectLatest {
            preloadChildrenOfTopLevelNodes()
          }
      }
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
      preloadChildren(model, path)
    }
  }

  private fun preloadChildrenOfTopLevelNodes() {
    if (hasCachedPresentation()) return
    val model = tree.model ?: return
    val root = model.root ?: return
    val rootPath = CachingTreePath(root)
    for (index in 0 until model.getChildCount(root)) {
      val child = model.getChild(root, index) ?: continue
      preloadChildren(model, rootPath.pathByAddingChild(child))
    }
  }

  private fun preloadChildren(model: TreeModel, path: TreePath) {
    val value = path.lastPathComponent
    if (model.isLeaf(value)) return
    if (model is BgtAwareTreeModel) {
      model.loadChildren(path)
    }
    else {
      model.getChildCount(value)
    }
  }

  private class UiListeners(
    private val tree: JTree,
    onVisibleRangeChange: () -> Unit,
    onTopLevelNodesChange: () -> Unit,
  ) {
    private val scrollPane: JScrollPane? = ComponentUtil.getParentOfType(JScrollPane::class.java, tree)
    private var scrollBar: JScrollBar? = scrollPane?.verticalScrollBar
    private var model: TreeModel? = tree.model

    private val cachedPresentationPropertyListener = PropertyChangeListener {
      onVisibleRangeChange()
      onTopLevelNodesChange()
    }

    private val resizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        onVisibleRangeChange()
      }
    }

    private val scrollBarAdjustmentListener = AdjustmentListener {
      onVisibleRangeChange()
    }

    private val scrollBarPropertyListener = PropertyChangeListener {
      scrollBar?.removeAdjustmentListener(scrollBarAdjustmentListener)
      scrollBar = scrollPane?.verticalScrollBar
      scrollBar?.addAdjustmentListener(scrollBarAdjustmentListener)
    }

    private val modelListener = object : TreeModelListener {
      override fun treeNodesInserted(e: TreeModelEvent) {
        if (e.isAboutTopLevelNodes) onTopLevelNodesChange()
      }

      override fun treeStructureChanged(e: TreeModelEvent) {
        if (e.isAboutTopLevelNodes) onTopLevelNodesChange()
      }

      override fun treeNodesChanged(e: TreeModelEvent) { }

      override fun treeNodesRemoved(e: TreeModelEvent) { }
    }

    private val modelPropertyListener = PropertyChangeListener {
      model?.removeTreeModelListener(modelListener)
      model = tree.model
      model?.addTreeModelListener(modelListener)
      onTopLevelNodesChange()
    }

    fun install() {
      tree.addPropertyChangeListener(CACHED_TREE_PRESENTATION_PROPERTY, cachedPresentationPropertyListener)
      tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, modelPropertyListener)
      model?.addTreeModelListener(modelListener)
      scrollPane?.addComponentListener(resizeListener)
      scrollBar?.addAdjustmentListener(scrollBarAdjustmentListener)
      scrollPane?.addPropertyChangeListener("verticalScrollBar", scrollBarPropertyListener)
    }

    fun uninstall() {
      scrollPane?.removePropertyChangeListener("verticalScrollBar", scrollBarPropertyListener)
      scrollBar?.removeAdjustmentListener(scrollBarAdjustmentListener)
      scrollPane?.removeComponentListener(resizeListener)
      model?.removeTreeModelListener(modelListener)
      tree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, modelPropertyListener)
      tree.removePropertyChangeListener(CACHED_TREE_PRESENTATION_PROPERTY, cachedPresentationPropertyListener)
    }
  }
}

/**
 * `true` for an event about the children of the root, and for an event without a path, such as a new root.
 */
private val TreeModelEvent.isAboutTopLevelNodes: Boolean
  get() = treePath?.parentPath == null
