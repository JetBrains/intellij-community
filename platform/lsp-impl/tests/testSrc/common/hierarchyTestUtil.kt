// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.common

import com.intellij.ide.DataManager
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpen
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.content.ContentManager
import com.intellij.util.OpenSourceUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Runs the hierarchy action [actionId] (Call Hierarchy, Type Hierarchy) in the editor, as the user does.
 * Returns the browser that the action opened in the Hierarchy tool window, or `null` when the action is disabled.
 * Close the browsers with [closeHierarchies] at the end of the test.
 */
internal suspend fun CodeInsightTestFixture.openHierarchy(actionId: String): HierarchyBrowserBaseEx? = withContext(Dispatchers.EDT) {
  val contentManager = hierarchyContentManager(project)
  val enabled = writeIntentReadAction { testAction(ActionManager.getInstance().getAction(actionId)).isEnabled }
  if (!enabled) {
    check(contentManager.contentCount == 0) { "A disabled action opened a browser" }
    return@withContext null
  }
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  selectedHierarchyBrowser(project)
}

/** The browser that the Hierarchy tool window shows. */
internal suspend fun selectedHierarchyBrowser(project: Project): HierarchyBrowserBaseEx = withContext(Dispatchers.EDT) {
  val content = checkNotNull(hierarchyContentManager(project).selectedContent) { "The Hierarchy tool window shows nothing" }
  // the content component may wrap the browser, see DumbService.wrapGently
  checkNotNull(UIUtil.uiTraverser(content.component).filter(HierarchyBrowserBaseEx::class.java).first()) {
    "No hierarchy browser in ${content.component}"
  }
}

internal suspend fun closeHierarchies(project: Project) {
  withContext(Dispatchers.EDT) {
    HierarchyBrowserManager.getInstance(project).contentManager?.removeAllContents(true)
  }
}

private fun hierarchyContentManager(project: Project): ContentManager {
  val manager = HierarchyBrowserManager.getInstance(project)
  // the manager gets its content manager from the tool window later, on the EDT
  if (manager.contentManager == null) PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  return checkNotNull(manager.contentManager) { "The Hierarchy tool window isn't registered" }
}

/** A node that the hierarchy tree shows. The visible root has depth 0. */
internal class HierarchyNode(val depth: Int, val descriptor: HierarchyNodeDescriptor, val path: TreePath) {
  val text: String get() = descriptor.highlightedText.text
}

/** What the tree of the current view shows, as indented node texts and as [nodes]. */
internal class HierarchyTree(val browser: HierarchyBrowserBaseEx, val tree: JTree, val nodes: List<HierarchyNode>) {
  val text: String get() = nodes.joinToString("\n") { "  ".repeat(it.depth) + it.text }

  fun node(text: String): HierarchyNode = nodes.single { it.text == text }
}

/**
 * Waits until the tree of the current view is built, expands the visible root, and returns what the tree shows.
 * The tree builds the nodes in the background, so the server gets the requests as it does in the IDE.
 */
internal suspend fun HierarchyBrowserBaseEx.shownTree(): HierarchyTree = withContext(Dispatchers.EDT) {
  val browser = this@shownTree
  // the views are cards of one panel, and only the card of the current view is visible
  val tree = UIUtil.uiTraverser(browser).filter(JTree::class.java).single { tree ->
    generateSequence<Component>(tree) { it.parent }.takeWhile { it !== browser }.all { it.isVisible }
  }
  PlatformTestUtil.waitWhileBusy(tree)
  // depth 2: the invisible base node and the visible root
  PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree, 2))
  PlatformTestUtil.waitWhileBusy(tree)

  val nodes = mutableListOf<HierarchyNode>()
  fun collect(path: TreePath, depth: Int) {
    if (depth >= 0) {
      val descriptor = checkNotNull(TreeUtil.getUserObject(HierarchyNodeDescriptor::class.java, path.lastPathComponent))
      nodes.add(HierarchyNode(depth, descriptor, path))
      if (!tree.isExpanded(path)) return
    }
    val node = path.lastPathComponent
    for (i in 0 until tree.model.getChildCount(node)) {
      collect(path.pathByAddingChild(tree.model.getChild(node, i)), depth + 1)
    }
  }
  collect(TreePath(tree.model.root), -1)
  HierarchyTree(browser, tree, nodes)
}

/** Switches the browser to the view [viewType], as the toolbar buttons do. */
internal suspend fun HierarchyBrowserBaseEx.showView(viewType: String) {
  withContext(Dispatchers.EDT) {
    changeView(viewType)
  }
}

/** Selects [node] and runs the browser action [actionId] on it, as a shortcut in the tree does. Returns whether the action was enabled. */
internal suspend fun HierarchyTree.runActionOnNode(actionId: String, node: HierarchyNode): Boolean = withContext(Dispatchers.EDT) {
  val dataContext = treeDataContext(node)
  val action = ActionManager.getInstance().getAction(actionId)
  val event = TestActionEvent.createTestEvent(action, dataContext)
  val enabled = writeIntentReadAction {
    ActionUtil.updateAction(action, event)
    if (event.presentation.isEnabled) ActionUtil.performAction(action, event)
    event.presentation.isEnabled
  }
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  enabled
}

/** Selects [node] and jumps to its source, as a double click or F4 in the tree does. */
internal suspend fun HierarchyTree.jumpToSource(node: HierarchyNode) {
  withContext(Dispatchers.EDT) {
    OpenSourceUtil.openSourcesFrom(treeDataContext(node), true)
  }
  checkNotNull(node.descriptor.project).awaitPendingNavigationAndEditorOpen()
}

/** Selects [node] and returns the data context of the tree, as an action in the tree gets it. */
private fun HierarchyTree.treeDataContext(node: HierarchyNode): DataContext {
  tree.selectionPath = node.path
  // HeadlessDataManager doesn't collect the data of a Swing component unless asked to
  val disposable = Disposer.newDisposable("hierarchy tree data context")
  try {
    HeadlessDataManager.fallbackToProductionDataManager(disposable)
    return DataManager.getInstance().getDataContext(tree)
  }
  finally {
    Disposer.dispose(disposable)
  }
}
