// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.debugger.mockJDI.MockVirtualMachine
import com.intellij.debugger.mockJDI.values.MockObjectReference
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.threadDumpParser.ThreadState
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ThreadDumpPanelSelectionTest : BasePlatformTestCase() {
  private lateinit var panel: ThreadDumpPanel
  private lateinit var consoleView: ConsoleView

  override fun setUp() {
    super.setUp()
    val uiState = UISettings.getInstance().state

    uiState.mergeEqualStackTraces = false
    uiState.showDumpItemsHierarchy = false
    uiState.showOnlyPlatformThreads = false
  }

  override fun tearDown() {
    try {
      Disposer.dispose(consoleView)
    } catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testSelectionAfterSwitchToShowOnlyPlatformThreads() {
    createPanel(
      thread("platform-thread-1", virtual = false),
      thread("platform-thread-2", virtual = false),
      thread("virtual-thread-1", virtual = true),
    )

    panel.selectStackFrame(1)
    assertSelectedThread("platform-thread-2")

    UISettings.getInstance().state.showOnlyPlatformThreads = true
    invokeUpdateThreadsTree()

    assertSelectedThread("platform-thread-2")
  }

  fun testSelectionFallsBackToFirstWhenVirtualThreadSelectedAndSwitchingToPlatformOnly() {
    createPanel(
      thread("platform-thread-1", virtual = false),
      thread("virtual-thread-1", virtual = true),
    )

    panel.selectStackFrame(1)
    assertSelectedThread("virtual-thread-1")

    UISettings.getInstance().state.showOnlyPlatformThreads = true
    invokeUpdateThreadsTree()

    assertSelectedThread("platform-thread-1")
  }

  fun testSelectionPreservedWhenSwitchingToVirtualThreadContainersMode() {
    val containerId = 100L
    createPanelWithContainers(
      threads = listOf(
        thread("platform-thread-1", virtual = false),
        threadWithContainer("virtual-thread-1", virtual = true, containerId),
      ),
      containers = listOf(container("Container", containerId)),
    )

    panel.selectStackFrame(0)
    assertSelectedThread("platform-thread-1")

    UISettings.getInstance().state.showDumpItemsHierarchy = true
    invokeUpdateThreadsTree()

    assertSelectedThread("platform-thread-1")
  }

  fun testSelectionPreservedInHierarchyWhenSwitchingFromContainersToFlat() {
    val containerId = 100L
    createPanelWithContainers(
      threads = listOf(
        thread("platform-thread-1", virtual = false),
        threadWithContainer("virtual-thread-1", virtual = true, containerId),
      ),
      containers = listOf(container("Container", containerId)),
    )

    UISettings.getInstance().state.showDumpItemsHierarchy = true
    invokeUpdateThreadsTree()

    selectThreadByName("platform-thread-1")
    assertSelectedThread("platform-thread-1")

    UISettings.getInstance().state.showDumpItemsHierarchy = false
    invokeUpdateThreadsTree()

    assertSelectedThread("platform-thread-1")
  }

  fun testSelectionPreservedWhenFilterMatchesPreviousSelection() {
    createPanel(
      thread("worker-thread-1", virtual = false, stackTrace = "at com.example.Worker.run"),
      thread("worker-thread-2", virtual = false, stackTrace = "at com.example.Worker.run"),
      thread("worker-thread-3", virtual = false, stackTrace = "at com.example.Worker.run"),
      thread("main-thread", virtual = false, stackTrace = "at com.example.Main.run"),
    )

    panel.selectStackFrame(1)
    assertSelectedThread("worker-thread-2")

    setFilterText("worker")

    assertSelectedThread("worker-thread-2")
  }

  fun testSelectionFallsBackToFirstWhenFilterDoesNotMatchPreviousSelection() {
    createPanel(
      thread("worker-thread-1", virtual = false, stackTrace = "at com.example.Worker.run"),
      thread("main-thread", virtual = false, stackTrace = "at com.example.Main.run"),
    )

    panel.selectStackFrame(0)
    assertSelectedThread("worker-thread-1")

    setFilterText("main")

    assertSelectedThread("main-thread")
  }

  private fun thread(name: String, virtual: Boolean, stackTrace: String = "java.lang.Thread.sleep"): ThreadState {
    val state = ThreadState(name, "waiting")
    state.setStackTrace("\"$name\"\n  $stackTrace", false)
    state.isVirtual = virtual
    state.uniqueId = name.hashCode().toLong()
    return state
  }

  private fun threadWithContainer(name: String, virtual: Boolean, containerId: Long): ThreadState {
    val state = thread(name, virtual)
    state.threadContainerUniqueId = containerId
    return state
  }

  private fun container(name: String, id: Long): JavaThreadContainerDesc {
    val vm = MockVirtualMachine()
    val ref = object : MockObjectReference(vm, Any()) {
      override fun uniqueID(): Long = id
    }
    return JavaThreadContainerDesc(name, ref, null)
  }

  private fun createPanel(vararg threads: ThreadState) {
    createPanelWithContainers(threads.toList(), emptyList())
  }

  private fun createPanelWithContainers(threads: List<ThreadState>, containers: List<JavaThreadContainerDesc>) {
    consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    val toolbarActions = DefaultActionGroup()
    val dumpItems = toDumpItems(threads, containers)
    panel = ThreadDumpPanel.createFromDumpItems(project, consoleView, toolbarActions, dumpItems)
  }

  private fun invokeUpdateThreadsTree() {
    val method = ThreadDumpPanel::class.java.getDeclaredMethod("updateThreadsTree")
    method.isAccessible = true
    method.invoke(panel)
  }

  private fun setFilterText(text: String) {
    val filterPanelField = ThreadDumpPanel::class.java.getDeclaredField("myFilterPanel")
    filterPanelField.isAccessible = true
    val filterPanel = filterPanelField.get(panel) as javax.swing.JPanel
    filterPanel.isVisible = true

    val filterField = ThreadDumpPanel::class.java.getDeclaredField("myFilterField")
    filterField.isAccessible = true
    val searchField = filterField.get(panel) as com.intellij.ui.SearchTextField
    searchField.text = text
  }

  private fun getTree(): javax.swing.JTree {
    val method = ThreadDumpPanel::class.java.getDeclaredMethod("getTree")
    method.isAccessible = true
    return method.invoke(panel) as javax.swing.JTree
  }

  private fun assertSelectedThread(expectedName: String) {
    val tree = getTree()
    val path = tree.selectionPath
    assertNotNull("Expected a selected node but selection was null", path)
    val node = path!!.lastPathComponent as DefaultMutableTreeNode
    val item = node.userObject as DumpItem
    assertEquals(expectedName, item.name)
  }

  private fun selectThreadByName(name: String) {
    val tree = getTree()
    val root = tree.model.root as DefaultMutableTreeNode
    val node = findNode(root, name)
    assertNotNull("Could not find node with name '$name'", node)
    tree.selectionPath = TreePath(node!!.path)
  }

  private fun findNode(node: DefaultMutableTreeNode, name: String): DefaultMutableTreeNode? {
    val item = node.userObject
    if (item is DumpItem && item.name == name) return node
    for (i in 0 until node.childCount) {
      val found = findNode(node.getChildAt(i) as DefaultMutableTreeNode, name)
      if (found != null) return found
    }
    return null
  }
}
