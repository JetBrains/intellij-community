// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.DerivedResultImpl
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.unwrapDelegate
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.BuildViewNodeAssertion
import com.intellij.platform.testFramework.assertion.assertIsNodeExpanded
import com.intellij.platform.testFramework.assertion.treeAssertion.userObject
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.ui.SimpleColoredComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TestApplication
class BuildTreeConsoleViewTest {

  private val buildDescriptor = DefaultBuildDescriptor(Any(), "test descriptor", "fake path", 1L)

  private val treeConsoleView by testFixture {
    val project = projectFixture().init()
    val view = withContext(Dispatchers.EDT) {
      BuildTreeConsoleView(project, buildDescriptor, BuildTextConsoleView(project, true, emptyList()))
    }
    initialized(view) {
      Disposer.dispose(view)
    }
  }

  @Test
  fun `test tree console handles event`() {
    BuildRootProgressImpl(treeConsoleView)
      .start("build Started", BuildProgressDescriptorImpl(buildDescriptor))

    assertBuildViewTree(treeConsoleView) {
      assertNode("build Started")
    }
  }

  @Test
  fun `test build level of tree console view are auto-expanded`() {
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event")
          .startChildProgress("build nested event")
          .finish(SuccessResultImpl(true))
        .finish(SuccessResultImpl(true))
      .finish("build finished", SuccessResultImpl(true))
    // @formatter:on

    assertBuildViewTree(treeConsoleView) {
      assertNode("build finished") {
        assertIsNodeExpanded(true)
        assertNode("build event") {
          assertIsNodeExpanded(false)
          assertNode("build nested event")
        }
      }
    }
  }

  @Test
  fun `test first message node is auto-expanded`() {
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event 1")
          .fileMessage("file message", "file detailed message", MessageEvent.Kind.WARNING, FilePosition(Path.of("a.file"), 0, 0))
        .finish(true)
        .startChildProgress("build event 2")
          .fileMessage("file message 1", "file detailed message 1", MessageEvent.Kind.WARNING, FilePosition(Path.of("a1.file"), 0, 0))
          .fileMessage("file message 2", "file detailed message 2", MessageEvent.Kind.WARNING, FilePosition(Path.of("a2.file"), 0, 0))
        .finish(true)
        .startChildProgress("build event 3")
          .fileMessage("file message 3", "file detailed message 3", MessageEvent.Kind.WARNING, FilePosition(Path.of("a3.file"), 0, 0))
          .fileMessage("file message with error", "file detailed message with error", MessageEvent.Kind.ERROR, FilePosition(Path.of("a4.file"), 5, 0))
        .finish(FailureResultImpl())
      .finish("build failed", FailureResultImpl())
    // @formatter:on

    assertBuildViewTree(treeConsoleView) {
      assertNode("build failed") {
        assertIsNodeExpanded(true)
        assertNode("build event 1") {
          assertIsNodeExpanded(true)
          assertNode("a.file") {
            assertIsNodeExpanded(true)
            assertNode("file message") {
              assertTitleElements("file message", " :1")
            }
          }
        }
        assertNode("build event 2") {
          assertIsNodeExpanded(false)
          assertNode("a1.file") {
            assertIsNodeExpanded(false)
            assertNode("file message 1")
          }
          assertNode("a2.file") {
            assertIsNodeExpanded(false)
            assertNode("file message 2")
          }
        }
        assertNode("build event 3") {
          assertIsNodeExpanded(true)
          assertNode("a3.file") {
            assertIsNodeExpanded(false)
            assertNode("file message 3")
          }
          assertNode("a4.file") {
            assertIsNodeExpanded(true)
            assertNode("file message with error") {
              assertTitleElements("file message with error", " :6")
            }
          }
        }
      }
    }
  }

  @Test
  fun `test derived result depend on child result - fail case`() {
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

    assertBuildViewTree(treeConsoleView) {
      assertNode("build finished") {
        assertHasFailures(true)
        assertNode("build event") {
          assertHasFailures(true)
          assertNode("build nested event") {
            assertHasFailures(true)
            assertNode("error message") {
              assertHasFailures(true)
            }
          }
        }
      }
    }
  }

  @Test
  fun `test derived result depend on child result - success case`() {
    // @formatter:off
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
        .startChildProgress("build event")
          .startChildProgress("build nested event")
          .finish(SuccessResultImpl())
        .finish(DerivedResultImpl())
      .finish("build finished", DerivedResultImpl())
    // @formatter:on

    assertBuildViewTree(treeConsoleView) {
      assertNode("build finished") {
        assertHasFailures(false)
        assertNode("build event") {
          assertHasFailures(false)
          assertNode("build nested event") {
            assertHasFailures(false)
          }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `test globally soft wraps are initialized in the selected console`(initGlobalSoftWraps: Boolean): Unit = timeoutRunBlocking {
    val editorSettings = EditorSettingsExternalizable.getInstance()
    val globalSoftWraps = editorSettings.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE)
    withContext(Dispatchers.EDT) {
      try {
        editorSettings.setUseSoftWraps(initGlobalSoftWraps, SoftWrapAppliancePlaces.CONSOLE)

        startBuild()
        val editor = getSelectedConsoleEditor()

        assertThat(editor.settings.isUseSoftWraps)
          .describedAs("Console editor should have soft wraps initialized to '$initGlobalSoftWraps' from global settings")
          .isEqualTo(initGlobalSoftWraps)
      }
      finally {
        editorSettings.setUseSoftWraps(globalSoftWraps, SoftWrapAppliancePlaces.CONSOLE)
      }
    }
  }

  @Test
  fun `test soft wrap action toggles soft wraps of the selected console`(): Unit = timeoutRunBlocking {
    val editorSettings = EditorSettingsExternalizable.getInstance()
    val globalSoftWraps = editorSettings.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE)
    withContext(Dispatchers.EDT) {
      try {
        editorSettings.setUseSoftWraps(true, SoftWrapAppliancePlaces.CONSOLE)

        startBuild()
        val editor = getSelectedConsoleEditor()
        val softWrapAction = findToolbarAction<ToggleUseSoftWrapsToolbarAction>()

        softWrapAction.setSelected(TestActionEvent.createTestEvent(softWrapAction), false)
        assertThat(editor.settings.isUseSoftWraps)
          .describedAs("Soft-Wrap action should toggle soft wraps of the console editor to off")
          .isFalse()

        softWrapAction.setSelected(TestActionEvent.createTestEvent(softWrapAction), true)
        assertThat(editor.settings.isUseSoftWraps)
          .describedAs("Soft-Wrap action should toggle soft wraps of the console editor back to on")
          .isTrue()
      }
      finally {
        editorSettings.setUseSoftWraps(globalSoftWraps, SoftWrapAppliancePlaces.CONSOLE)
      }
    }
  }

  @Test
  fun `test scroll to the end action moves the caret to the end of the selected console`(): Unit = timeoutRunBlocking {
    startBuild()
    printToSelectedConsole { console ->
      repeat(10) {
        console.print("build output line $it\n", ConsoleViewContentType.NORMAL_OUTPUT)
      }
    }
    val editor = getSelectedConsoleEditor()
    val scrollToTheEndAction = findToolbarAction<ScrollToTheEndToolbarAction>()

    withContext(Dispatchers.EDT) {
      assertThat(editor.document.textLength)
        .describedAs("The console should contain the printed build output")
        .isGreaterThan(0)

      editor.caretModel.moveToOffset(0)
      assertThat(editor.caretModel.offset).isEqualTo(0)

      scrollToTheEndAction.actionPerformed(TestActionEvent.createTestEvent(scrollToTheEndAction))

      assertThat(editor.caretModel.offset)
        .describedAs("Scroll to End action should move the caret to the end of the console editor")
        .isEqualTo(editor.document.textLength)
    }
  }

  private fun startBuild() {
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
  }

  /**
   * Prints [printOutput] into the currently selected console
   */
  private suspend fun printToSelectedConsole(printOutput: (ConsoleViewImpl) -> Unit = {}) {
    withContext(Dispatchers.EDT) {
      val console = assertInstanceOf<ConsoleViewImpl>(treeConsoleView.currentConsole?.unwrapDelegate())
      printOutput(console)
      console.flushDeferredText()
    }
  }

  private suspend fun getSelectedConsoleEditor(): Editor = withContext(Dispatchers.EDT) {
    val editor = treeConsoleView.currentConsoleEditor
    assertNotNull(editor) { "The current console editor is expected to be initialized" }
    return@withContext editor
  }

  private inline fun <reified T : Any> findToolbarAction(): T {
    val actions = treeConsoleView.consoleToolbarActionGroup.childActionsOrStubs.toList()
    val matched = actions.filterIsInstance<T>()
    assertThat(matched)
      .describedAs("Expected a single ${T::class.java.simpleName} in the console toolbar, but got: $actions")
      .hasSize(1)
    return matched.single()
  }

  companion object {

    private fun BuildViewNodeAssertion.assertHasFailures(expected: Boolean) {
      assertValue {
        val executionNode = it.treeConsoleView.findNode(it.treePath.userObject) ?: throw AssertionError(
          "Cannot find ExecutionNode for TreePath: ${it.treePath}"
        )
        assertThat(executionNode.isFailed || executionNode.hasWarnings())
          .describedAs { "Failure status assertion for TreePath: ${it.treePath}" }
          .isEqualTo(expected)
      }
    }

    fun BuildViewNodeAssertion.assertTitleElements(vararg expected: String) {
      assertValue {
        val tree = it.treeConsoleView.tree
        val node = it.treePath.lastPathComponent
        val component = tree.cellRenderer.getTreeCellRendererComponent(tree, node, false, false, false, 0, false)
        assertThat(Sequence { (component as SimpleColoredComponent).iterator() }.toList())
          .describedAs { "Title elements assertion for TreePath: ${it.treePath}" }
          .containsExactly(*expected)
      }
    }
  }
}
