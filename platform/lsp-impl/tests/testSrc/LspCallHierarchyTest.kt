// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCallHierarchyCustomizer
import com.intellij.platform.lsp.api.customization.LspCallHierarchyDisabled
import com.intellij.platform.lsp.api.customization.LspCallHierarchySupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.common.closeHierarchies
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.jumpToSource
import com.intellij.platform.lsp.common.nullResponse
import com.intellij.platform.lsp.common.openHierarchy
import com.intellij.platform.lsp.common.runActionOnNode
import com.intellij.platform.lsp.common.selectedHierarchyBrowser
import com.intellij.platform.lsp.common.showView
import com.intellij.platform.lsp.common.shownTree
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [Call hierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareCallHierarchy)
 * through the Call Hierarchy action and the browser it opens in the Hierarchy tool window.
 */
@TestApplication
internal class LspCallHierarchyTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** `foo` is declared on line 0 and called from `bar` on line 1. */
    private const val TEXT: String = "fun f<caret>oo() {}\nfun bar() { foo() }"
    private val FOO_POSITION: Position = Position(0, 5)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @AfterEach
  fun closeHierarchyBrowsers() = timeoutRunBlocking {
    closeHierarchies(project)
  }

  private suspend fun openCallHierarchy(): HierarchyBrowserBaseEx? = codeInsightFixture.openHierarchy(IdeActions.ACTION_CALL_HIERARCHY)

  private suspend fun requireCallHierarchy(): HierarchyBrowserBaseEx =
    checkNotNull(openCallHierarchy()) { "The Call Hierarchy action should be enabled at the caret" }

  private fun fooItem(uri: String): CallHierarchyItem =
    CallHierarchyItem("foo", SymbolKind.Function, uri, range(0, 0, 0, 15), range(0, 4, 0, 7))

  private fun barItem(uri: String): CallHierarchyItem =
    CallHierarchyItem("bar", SymbolKind.Method, uri, range(1, 0, 1, 19), range(1, 4, 1, 7))

  @Nested
  inner class CallHierarchySupported {
    private val fakeLspIntegration by callHierarchyIntegrationFixture()

    @Test
    fun `the caller tree root is the prepared item and its children are the incoming calls`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls1.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY, {
        it.textDocument.uri == fileUri && it.position == FOO_POSITION
      }) {
        listOf(fooItem(fileUri))
      }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS, { it.item.name == "foo" }) {
        listOf(CallHierarchyIncomingCall(barItem(fileUri), listOf(range(1, 12, 1, 15))))
      }

      val tree = requireCallHierarchy().shownTree()

      assertEquals("foo\n  bar", tree.text)
      assertEquals(virtualFile, tree.node("bar").descriptor.containingFile?.virtualFile)
      serverSession.awaitExpected()
    }

    @Test
    fun `the callee tree children are the targets of the outgoing calls`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls2.txt", "fun foo() {}\nfun b<caret>ar() { foo() }").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      // the caller view opens first, and each view prepares its own root
      repeat(2) {
        serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY, { it.position == Position(1, 5) }) {
          listOf(barItem(fileUri))
        }
      }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS) { emptyList() }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_OUTGOING_CALLS, { it.item.name == "bar" }) {
        listOf(CallHierarchyOutgoingCall(fooItem(fileUri), listOf(range(1, 12, 1, 15))))
      }

      val browser = requireCallHierarchy()
      assertEquals("bar", browser.shownTree().text)
      browser.showView(CallHierarchyBrowserBase.getCalleeType())
      val tree = browser.shownTree()

      assertEquals("bar\n  foo", tree.text)
      serverSession.awaitExpected()
    }

    @Test
    fun `the item data goes back to the server unchanged`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls3.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val data = mapOf("id" to 42, "nested" to mapOf("path" to listOf("a", "b"), "flag" to true))
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) {
        listOf(fooItem(fileUri).apply { this.data = data })
      }
      val incomingCallsRequested = serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS, {
        it.item.data.toString() == """{"id":42,"nested":{"path":["a","b"],"flag":true}}""" &&
        it.item.uri == fileUri &&
        it.item.selectionRange == range(0, 4, 0, 7)
      }) {
        emptyList()
      }

      requireCallHierarchy().shownTree()

      assertTrue(incomingCallsRequested.isCompleted, "incomingCalls should carry the prepared item with its data")
      serverSession.awaitExpected()
    }

    @Test
    fun `a node shows the name, the detail and the icon of its kind`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls4.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) {
        listOf(fooItem(fileUri).apply { detail = "(): Unit" })
      }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS) {
        listOf(CallHierarchyIncomingCall(barItem(fileUri), listOf(range(1, 12, 1, 15))))
      }

      val tree = requireCallHierarchy().shownTree()

      assertEquals("foo : (): Unit\n  bar", tree.text, "A node without detail shows only the name")
      assertSame(AllIcons.Nodes.Function, tree.node("foo : (): Unit").descriptor.icon)
      assertSame(AllIcons.Nodes.Method, tree.node("bar").descriptor.icon)
      serverSession.awaitExpected()
    }

    @Test
    fun `a caller in an unknown file is skipped`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls5.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) { listOf(fooItem(fileUri)) }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS) {
        val missingFileUri = fileUri.substringBeforeLast('/') + "/missing.txt"
        listOf(
          CallHierarchyIncomingCall(CallHierarchyItem("lost", SymbolKind.Function, missingFileUri, range(0, 0, 0, 4), range(0, 0, 0, 4)),
                                    listOf(range(0, 0, 0, 4))),
          CallHierarchyIncomingCall(barItem(fileUri), listOf(range(1, 12, 1, 15))),
        )
      }

      val tree = requireCallHierarchy().shownTree()

      assertEquals("foo\n  bar", tree.text)
      serverSession.awaitExpected()
    }

    @Test
    fun `a caller in another file belongs to that file`() = timeoutRunBlocking {
      val otherFile = codeInsightFixture.addFileToProject("other1.txt", "fun baz() { foo() }").virtualFile
      val virtualFile = codeInsightFixture.configureByText("calls6.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val otherUri = serverSession.fileUri(otherFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) { listOf(fooItem(fileUri)) }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS) {
        val baz = CallHierarchyItem("baz", SymbolKind.Function, otherUri, range(0, 0, 0, 19), range(0, 4, 0, 7))
        listOf(CallHierarchyIncomingCall(baz, listOf(range(0, 12, 0, 15))))
      }

      val tree = requireCallHierarchy().shownTree()

      assertEquals(otherFile, tree.node("baz").descriptor.containingFile?.virtualFile)
      serverSession.awaitExpected()
    }

    @Test
    @Disabled("The root node always gets the PSI file of the editor, not the file of the prepared item's URI")
    fun `a prepared item in another file makes the root belong to that file`() = timeoutRunBlocking {
      val otherFile = codeInsightFixture.addFileToProject("other2.txt", "fun foo() {}").virtualFile
      val virtualFile = codeInsightFixture.configureByText("calls7.txt", "f<caret>oo()").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val otherUri = serverSession.fileUri(otherFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) {
        listOf(CallHierarchyItem("foo", SymbolKind.Function, otherUri, range(0, 0, 0, 12), range(0, 4, 0, 7)))
      }

      val tree = requireCallHierarchy().shownTree()

      assertEquals(otherFile, tree.node("foo").descriptor.containingFile?.virtualFile)
      serverSession.awaitExpected()
    }

    @Test
    fun `navigating to a caller moves the caret to its selection range start`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls8.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) { listOf(fooItem(fileUri)) }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS) {
        listOf(CallHierarchyIncomingCall(barItem(fileUri), listOf(range(1, 12, 1, 15))))
      }
      val tree = requireCallHierarchy().shownTree()

      tree.jumpToSource(tree.node("bar"))

      val (selectedFile, caretOffset) = withContext(Dispatchers.EDT) {
        val editor = checkNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
        editor.virtualFile to editor.caretModel.offset
      }
      assertEquals(virtualFile, selectedFile)
      assertEquals("fun foo() {}\nfun ".length, caretOffset)
      serverSession.awaitExpected()
    }

    @Test
    fun `base on this prepares the hierarchy at the position of the node in its own file`() = timeoutRunBlocking {
      val otherFile = codeInsightFixture.addFileToProject("other3.txt", "fun baz() { foo() }").virtualFile
      val virtualFile = codeInsightFixture.configureByText("calls9.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val otherUri = serverSession.fileUri(otherFile)
      val baz = CallHierarchyItem("baz", SymbolKind.Function, otherUri, range(0, 0, 0, 19), range(0, 4, 0, 7))
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY, { it.textDocument.uri == fileUri }) { listOf(fooItem(fileUri)) }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS, { it.item.name == "foo" }) {
        listOf(CallHierarchyIncomingCall(baz, listOf(range(0, 12, 0, 15))))
      }
      val tree = requireCallHierarchy().shownTree()
      val rePrepared = serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY, {
        it.textDocument.uri == otherUri && it.position == Position(0, 4)
      }) {
        listOf(baz)
      }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS, { it.item.name == "baz" }) { emptyList() }

      val enabled = tree.runActionOnNode("CallHierarchy.BaseOnThisMethod", tree.node("baz"))
      val rebasedTree = selectedHierarchyBrowser(project).shownTree()

      assertTrue(enabled, "Base on This should be enabled for a caller")
      assertTrue(rePrepared.isCompleted)
      assertEquals("baz", rebasedTree.text)
      serverSession.awaitExpected()
    }

    @Test
    fun `an empty prepare answer gives an empty tree`() = timeoutRunBlocking {
      assertEmptyTree("calls10.txt") { emptyList() }
    }

    @Test
    fun `a null prepare answer gives an empty tree`() = timeoutRunBlocking {
      assertEmptyTree("calls11.txt") { nullResponse() }
    }

    @Test
    fun `an error prepare answer gives an empty tree`() = timeoutRunBlocking {
      assertEmptyTree("calls12.txt") {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "cannot prepare", null))
      }
    }

    @Test
    fun `an error incomingCalls answer gives a node without children`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls13.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) { listOf(fooItem(fileUri)) }
      serverSession.expectRequest(serverSession.CALL_HIERARCHY_INCOMING_CALLS) {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "cannot find callers", null))
      }

      val tree = requireCallHierarchy().shownTree()

      assertEquals("foo", tree.text)
      serverSession.awaitExpected()
    }

    private suspend fun assertEmptyTree(fileName: String, prepareAnswer: suspend () -> List<CallHierarchyItem>) {
      val virtualFile = codeInsightFixture.configureByText(fileName, TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_CALL_HIERARCHY) { prepareAnswer() }

      val tree = requireCallHierarchy().shownTree()

      assertEquals("", tree.text)
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class NoCallHierarchyCapability {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `the action is disabled when the server has no call hierarchy capability`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls14.txt", TEXT).virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      assertNull(openCallHierarchy())
    }
  }

  @Nested
  inner class ShouldAskServerForCallHierarchyFalse {
    private val fakeLspIntegration by callHierarchyIntegrationFixture(object : LspCallHierarchySupport() {
      override fun shouldAskServerForCallHierarchy(file: VirtualFile): Boolean = false
    })

    @Test
    fun `the action is disabled when the customizer declines the file`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls15.txt", TEXT).virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      assertNull(openCallHierarchy())
    }
  }

  @Nested
  inner class CallHierarchyDisabled {
    private val fakeLspIntegration by callHierarchyIntegrationFixture(LspCallHierarchyDisabled)

    @Test
    fun `the action is disabled when the customizer disables the feature`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("calls16.txt", TEXT).virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      assertNull(openCallHierarchy())
    }
  }

  private fun callHierarchyIntegrationFixture(customizer: LspCallHierarchyCustomizer = LspCallHierarchySupport()) =
    projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val callHierarchyCustomizer: LspCallHierarchyCustomizer = customizer
      },
      configureServerCapabilities = {
        callHierarchyProvider = Either.forLeft(true)
      },
    )

  private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
    Range(Position(startLine, startChar), Position(endLine, endChar))
}
