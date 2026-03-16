// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.DerivedResultImpl
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.showAllNodes
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.tree.TreeUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

class BuildTreeConsoleViewTest : LightPlatformTestCase() {

  companion object {
    val LOG: Logger = Logger.getInstance(BuildTreeConsoleViewTest::class.java)
  }

  lateinit var treeConsoleView: BuildTreeConsoleView
  lateinit var buildDescriptor: BuildDescriptor

  override fun setUp() {
    super.setUp()
    buildDescriptor = DefaultBuildDescriptor(Any(), "test descriptor", "fake path", 1L)
    treeConsoleView = BuildTreeConsoleView(project, buildDescriptor, null)
  }

  @Test
  fun `test tree console handles event`() {
    val tree = treeConsoleView.tree
    val message = "build Started"
    BuildRootProgressImpl(treeConsoleView)
      .start(message, BuildProgressDescriptorImpl(buildDescriptor))

    waitUntilAssertSucceedsBlocking {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.assertTreeEqual(tree, "-\n" +
                                             " build Started")
    }

    val visitor = CollectingTreeVisitor(tree)
    TreeUtil.visitVisibleRows(tree, visitor)
    assertThat(visitor.presentations)
      .extracting("name")
      .containsExactly(message)
  }

  @Test
  fun `test build level of tree console view are auto-expanded`() {
    val tree = treeConsoleView.tree
    showAllNodes(treeConsoleView)
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event")
          .startChildProgress("build nested event")
          .finish(SuccessResultImpl(true))
        .finish(SuccessResultImpl(true))
      .finish("build finished", SuccessResultImpl(true))
    // @formatter:on

    waitUntilAssertSucceedsBlocking {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.assertTreeEqual(tree, "-\n" +
                                             " -build finished\n" +
                                             "  +build event")
    }

    val visitor = CollectingTreeVisitor(tree)
    TreeUtil.visitVisibleRows(tree, visitor)
    assertThat(visitor.presentations)
      .extracting("name")
      .contains("build finished", "build event")
  }

  @Test
  fun `test first message node is auto-expanded`() {
    val tree = treeConsoleView.tree
    showAllNodes(treeConsoleView)
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event 1")
          .fileMessage("file message", "file detailed message", MessageEvent.Kind.WARNING, FilePosition(File("a.file"), 0, 0))
        .finish(true)
        .startChildProgress("build event 2")
          .fileMessage("file message 1", "file detailed message 1", MessageEvent.Kind.WARNING, FilePosition(File("a1.file"), 0, 0))
          .fileMessage("file message 2", "file detailed message 2", MessageEvent.Kind.WARNING, FilePosition(File("a2.file"), 0, 0))
        .finish(true)
        .startChildProgress("build event 3")
          .fileMessage("file message 3", "file detailed message 3", MessageEvent.Kind.WARNING, FilePosition(File("a3.file"), 0, 0))
          .fileMessage("file message with error", "file detailed message with error", MessageEvent.Kind.ERROR, FilePosition(File("a4.file"), 5, 0))
        .finish(FailureResultImpl())
      .finish("build failed", FailureResultImpl())
    // @formatter:on

    waitUntilAssertSucceedsBlocking {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.assertTreeEqual(tree, "-\n" +
                                             " -build failed\n" +
                                             "  -build event 1\n" +
                                             "   -a.file\n" +
                                             "    file message\n" +
                                             "  +build event 2\n" +
                                             "  -build event 3\n" +
                                             "   +a3.file\n" +
                                             "   -a4.file\n" +
                                             "    file message with error")
    }

    val visitor = CollectingTreeVisitor(tree)
    TreeUtil.visitVisibleRows(tree, visitor)
    assertThat(visitor.presentations)
      .extracting("completeText")
      .contains(
        "file message => :1",
        "file message with error => :6"
      )
  }

  @Test
  fun `test derived result depend on child result - fail case`() {
    showAllNodes(treeConsoleView)
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event")
          .startChildProgress( "build nested event")
            .message("error message", "detailed error message", MessageEvent.Kind.ERROR, null)
          .finish(FailureResultImpl())
        .finish(DerivedResultImpl())
      .finish("build finished", DerivedResultImpl())
    // @formatter:on

    val tree = treeConsoleView.tree

    waitUntilAssertSucceedsBlocking {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.assertTreeEqual(tree, "-\n" +
                                             " -build finished\n" +
                                             "  -build event\n" +
                                             "   -build nested event\n" +
                                             "    error message")
    }

    val visitor = CollectingTreeVisitor(tree)
    TreeUtil.visitVisibleRows(tree, visitor)


    assertThat(visitor.presentations.map { "${it.name}--${it.failure}" })
      .containsExactly("build finished--true", "build event--true", "build nested event--true",
                       "error message--true")
  }

  @Test
  fun `test derived result depend on child result - success case`() {
    showAllNodes(treeConsoleView)
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event")
          .startChildProgress("build nested event")
          .finish(SuccessResultImpl())
        .finish(DerivedResultImpl())
      .finish("build finished", DerivedResultImpl())
    // @formatter:on

    val tree = treeConsoleView.tree

    waitUntilAssertSucceedsBlocking {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.assertTreeEqual(tree, "-\n" +
                                             " -build finished\n" +
                                             "  +build event")
    }

    val visitor = CollectingTreeVisitor(tree)
    TreeUtil.visitVisibleRows(tree, visitor)

    assertThat(visitor.presentations.map { it.name + "--" + it.failure })
      .containsExactly("build finished--false", "build event--false")

  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { Disposer.dispose(treeConsoleView) },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }
}

class CollectingTreeVisitor(private val tree: JTree) : TreeVisitor {
  val renderer: TreeCellRenderer = tree.cellRenderer
  val presentations = mutableListOf<Presentation>()

  override fun visit(path: TreePath): TreeVisitor.Action {
    val node = path.lastPathComponent
    val component = renderer.getTreeCellRendererComponent(tree, node, false, false, false, 0, false) as SimpleColoredComponent
    var name: String? = null
    val allText = mutableListOf<String>()
    val iterator = component.iterator()
    while (iterator.hasNext()) {
      val text = iterator.next()
      allText.add(text)
      if (iterator.textAttributes == SimpleTextAttributes.REGULAR_ATTRIBUTES) {
        name = text
      }
    }
    val failure = when(val userObject = TreeUtil.getUserObject(node)) {
      is ExecutionNode -> userObject.isFailed || userObject.hasWarnings()
      is BuildTreeNode -> userObject.hasProblems
      else -> error("Unknown node type: ${node.javaClass}")
    }
    presentations.add(Presentation(name ?: "", allText.joinToString(" =>"), failure))
    return TreeVisitor.Action.CONTINUE
  }

  data class Presentation(val name: String, val completeText: String, val failure: Boolean)
}
