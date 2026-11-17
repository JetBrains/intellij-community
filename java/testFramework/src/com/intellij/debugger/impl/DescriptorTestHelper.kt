// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.DebuggerTestFixture
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.SmartList
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.Value
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
import javax.swing.tree.TreeNode

/**
 * Creates a [DescriptorTestHelper] on the fixture of [debugger].
 * Declare it as an instance field after the debugger fixture.
 */
@TestOnly
fun descriptorTestHelperFixture(debugger: TestFixture<DebuggerTestFixture>): TestFixture<DescriptorTestHelper> =
  testFixture("descriptorTestHelperFixture") {
    initialized(DescriptorTestHelper(debugger.init())) {}
  }

/**
 * The JUnit 6 replacement of the members of [DescriptorTestCase]: the descriptor log, the renderer getters and [expandAll].
 *
 * The constructor changes [debugger] as the JUnit 3 base did:
 * - Each [DebuggerTestFixture.resume] prints the logged descriptors first, on the debugger manager thread after
 *   [DebuggerTestFixture.invokeRatherLater]. Then it resumes.
 * - An [DebuggerTestFixture.atDebuggerTearDown] action prints the descriptors that are still in the log.
 * - Until the end of the test, the data context of the application gives the [XDebugSession] of [DebuggerTestFixture.debuggerSession],
 *   The other data comes from the project, as in `HeavyPlatformTestCase`.
 *
 * Create one helper for each test, for example with [descriptorTestHelperFixture].
 */
@TestOnly
class DescriptorTestHelper(private val debugger: DebuggerTestFixture) {
  private val descriptorLog = LinkedHashMap<Any, NodeDescriptorText>()

  init {
    debugger.atDebuggerTearDown { flushDescriptors() }
    debugger.resumeHandler = Consumer { resume(it) }
    @Suppress("UsagesOfObsoleteApi")
    TestApplicationManager.getInstance().setDataProvider(DataProvider { dataId -> getData(dataId) }, debugger.disposable)
  }

  /** The renderer that shows the `toString()` of an object. */
  val toStringRenderer: NodeRenderer
    get() = NodeRendererSettings.getInstance().toStringRenderer

  /** The alternate renderer of a map. */
  val mapRenderer: NodeRenderer
    get() = getAlternateCollectionRenderer("Map")

  /** The alternate renderer of a map entry. */
  val mapEntryRenderer: NodeRenderer
    get() = getAlternateCollectionRenderer("Map.Entry")

  /** The renderer that shows a number in hex. */
  val hexRenderer: NodeRenderer
    get() = NodeRendererSettings.getInstance().hexRenderer

  /** The alternate renderer of a collection. */
  val collectionRenderer: NodeRenderer
    get() = getAlternateCollectionRenderer("Collection")

  /** Prints the logged descriptors, then resumes [suspendContext] with [DebuggerTestFixture.resume] of the fixture. */
  fun resume(suspendContext: SuspendContextImpl) {
    debugger.invokeRatherLater(object : SuspendContextCommandImpl(suspendContext) {
      override fun contextAction(suspendContext: SuspendContextImpl) {
        flushDescriptors()
        debugger.resumeNow(suspendContext)
      }
    })
  }

  /** Adds [text] to the log of [descriptor]. The same text twice in a row is logged once. */
  fun logDescriptor(descriptor: Any, text: String) {
    descriptorLog.getOrPut(descriptor) { NodeDescriptorText() }.appendText(text)
  }

  /** Sets the label of [descriptor] in the log. */
  fun logDescriptorLabel(descriptor: Any, label: String?) {
    descriptorLog.getOrPut(descriptor) { NodeDescriptorText() }.label = label
  }

  /** Prints the log to the output, sorted by the `toString()` of each descriptor, and clears the log. */
  fun flushDescriptors() {
    descriptorLog.entries
      .sortedBy { it.key.toString() }
      .forEach { it.value.print() }
    descriptorLog.clear()
  }

  /**
   * Expands the collapsed rows of [tree] on the debugger manager thread of [context], until no row expands.
   * A row with a value that is in [alreadyExpanded] does not expand again. Then it runs [runnable].
   */
  fun expandAll(
    tree: Tree,
    runnable: Runnable,
    alreadyExpanded: MutableSet<in Value>,
    filter: NodeFilter?,
    context: SuspendContextImpl,
  ) {
    debugger.invokeRatherLater(context) {
      var anyCollapsed = false
      for (i in 0 until tree.rowCount) {
        val treeNode = tree.getPathForRow(i).lastPathComponent as TreeNode
        if (tree.isCollapsed(i) && !treeNode.isLeaf) {
          val nodeDescriptor = (treeNode as? DebuggerTreeNodeImpl)?.descriptor
          var shouldExpand = filter == null || filter.shouldExpand(treeNode)
          // additional checks to prevent infinite expand
          if (shouldExpand && nodeDescriptor is ValueDescriptor) {
            val value = nodeDescriptor.value
            shouldExpand = !alreadyExpanded.contains(value)
            if (shouldExpand) {
              alreadyExpanded.add(value)
            }
          }
          if (shouldExpand) {
            anyCollapsed = true
            tree.expandRow(i)
          }
        }
      }

      if (anyCollapsed) {
        expandAll(tree, runnable, alreadyExpanded, filter, context)
      }
      else {
        runnable.run()
      }
    }
  }

  /** Decides which nodes [expandAll] expands. */
  fun interface NodeFilter {
    fun shouldExpand(node: TreeNode): Boolean
  }

  private fun getData(dataId: String): Any? {
    if (XDebugSession.DATA_KEY.`is`(dataId)) {
      return debugger.debuggerSession?.xDebugSession
    }
    val project = debugger.project
    return if (project.isDisposed) null else TestDataProvider(project).getData(dataId)
  }

  private inner class NodeDescriptorText {
    val text: MutableList<String> = SmartList()
    var label: String? = null

    fun appendText(text: String) {
      if (text != this.text.lastOrNull()) {
        this.text.add(text)
      }
    }

    fun print() {
      for (line in text) {
        debugger.execution.print(line, ProcessOutputTypes.SYSTEM)
      }
      val label = label
      if (!label.isNullOrEmpty()) {
        debugger.execution.print(label, ProcessOutputTypes.SYSTEM)
      }
    }
  }

  companion object {
    /** Expands one collapsed row of [tree] on the EDT and runs [wait], until no collapsed row is left. */
    @JvmStatic
    fun expandAll(tree: Tree, wait: Runnable) {
      while (true) {
        val expanded = UIUtil.invokeAndWaitIfNeeded<Boolean> { expandOne(tree) }
        wait.run()
        if (!expanded) {
          break
        }
      }
    }

    private fun expandOne(tree: Tree): Boolean {
      for (i in 0 until tree.rowCount) {
        val treeNode = tree.getPathForRow(i).lastPathComponent as TreeNode
        if (tree.isCollapsed(i) && !treeNode.isLeaf) {
          tree.expandRow(i)
          return true
        }
      }
      return false
    }

    private fun getAlternateCollectionRenderer(name: String): NodeRenderer =
      NodeRendererSettings.getInstance().alternateCollectionRenderers.firstOrNull { it.name == name }
      ?: error("No alternate collection renderer '$name'")
  }
}
