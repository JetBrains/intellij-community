// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSelectionRangeCustomizer
import com.intellij.platform.lsp.api.customization.LspSelectionRangeDisabled
import com.intellij.platform.lsp.api.customization.LspSelectionRangeSupport
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.SpaceTokenizingFileType
import com.intellij.platform.lsp.common.UnansweredRequest
import com.intellij.platform.lsp.common.assertCustomPsiTree
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.nullResponse
import com.intellij.platform.lsp.common.spaceTokenizingLanguageFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specification/#textDocument_selectionRange)
 * behind Extend Selection.
 */
@TestApplication
internal class LspSelectionRangeTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private const val TEXT: String = "aaa bbb c<caret>cc ddd\neee fff"
    private val CARET_POSITION: Position = Position(0, 9)
    private val WORD: TextRange = TextRange(8, 11)

    /** `ccc`, then `bbb ccc`, then `bbb ccc ddd`. Without the server, the second step would select the whole line. */
    private fun serverChain(): SelectionRange =
      SelectionRange(range(0, 8, 0, 11), SelectionRange(range(0, 4, 0, 11), SelectionRange(range(0, 4, 0, 15), null)))

    private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
      Range(Position(startLine, startChar), Position(endLine, endChar))
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private suspend fun extendSelection(): TextRange = withContext(Dispatchers.EDT) {
    codeInsightFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    val selectionModel = codeInsightFixture.editor.selectionModel
    TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
  }

  /** Cancels the expectation, so don't call [FakeLspServerSession.awaitExpected] afterward. */
  private fun assertNotRequested(request: Deferred<Unit>) {
    assertFalse(request.isCompleted, "No selectionRange request is expected")
    request.cancel()
  }

  @Nested
  inner class SelectionRangeSupported {
    private val fakeLspIntegration by selectionRangeIntegrationFixture()

    @Test
    fun `extending repeatedly follows the server ranges`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("select1.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.SELECTION_RANGE, {
        it.textDocument.uri == fileUri && it.positions == listOf(CARET_POSITION)
      }) {
        listOf(serverChain())
      }

      val selections = List(3) { extendSelection() }

      assertEquals(listOf(WORD, TextRange(4, 11), TextRange(4, 15)), selections)
      serverSession.awaitExpected()
    }

    @Test
    fun `extending three times sends one request`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("select2.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val firstRequest = serverSession.expectRequest(serverSession.SELECTION_RANGE) { listOf(serverChain()) }
      val secondRequest = serverSession.expectRequest(serverSession.SELECTION_RANGE) { listOf(serverChain()) }

      repeat(3) { extendSelection() }

      firstRequest.await()
      assertNotRequested(secondRequest)
    }

    @Test
    fun `an edit makes the next extend ask again`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("select3.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(serverSession.SELECTION_RANGE) { listOf(serverChain()) }
      val requestAfterEdit = serverSession.expectRequest(serverSession.SELECTION_RANGE) { listOf(serverChain()) }
      extendSelection()

      writeCommandAction(codeInsightFixture.project, "typing") {
        val editor = codeInsightFixture.editor
        editor.selectionModel.removeSelection()
        editor.document.insertString(editor.document.textLength, " ggg")
      }
      val selection = extendSelection()

      assertEquals(WORD, selection)
      requestAfterEdit.await()
      serverSession.awaitExpected()
    }

    @Test
    fun `a parent range outside the document is skipped and the outer ranges still apply`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("select4.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(serverSession.SELECTION_RANGE) {
        val outer = SelectionRange(range(0, 4, 0, 15), null)
        val outsideDocument = SelectionRange(range(0, 4, 7, 0), outer)
        listOf(SelectionRange(range(0, 8, 0, 11), outsideDocument))
      }

      val selections = List(2) { extendSelection() }

      assertEquals(listOf(WORD, TextRange(4, 15)), selections)
      serverSession.awaitExpected()
    }

    @Test
    fun `a server slower than 100 ms leaves the word selection and the request is cancelled`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("select5.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val pendingResponse = UnansweredRequest()
      serverSession.expectRequest(serverSession.SELECTION_RANGE) { pendingResponse.hold() }

      val selection = extendSelection()

      assertEquals(WORD, selection)
      waitUntil("The client should cancel the request after the timeout") { pendingResponse.isCancelled }
      serverSession.awaitExpected()
    }

    @Test
    fun `a null response leaves the word selection`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("select6.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(serverSession.SELECTION_RANGE) { nullResponse() }

      val selection = extendSelection()

      assertEquals(WORD, selection)
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class SpaceTokenizedFile {
    @Suppress("unused")
    private val spaceTokenizingLang = spaceTokenizingLanguageFixture()

    private val fakeLspIntegration by selectionRangeIntegrationFixture()

    @Test
    fun `one request when the platform asks the handler for each PSI ancestor`() = timeoutRunBlocking {
      val psiFile = codeInsightFixture.configureByText(SpaceTokenizingFileType, TEXT)
      assertCustomPsiTree(psiFile)
      val virtualFile = psiFile.virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val firstRequest = serverSession.expectRequest(serverSession.SELECTION_RANGE, { it.positions == listOf(CARET_POSITION) }) {
        listOf(serverChain())
      }
      val secondRequest = serverSession.expectRequest(serverSession.SELECTION_RANGE) { listOf(serverChain()) }

      val selections = List(3) { extendSelection() }

      assertEquals(listOf(WORD, TextRange(4, 11), TextRange(4, 15)), selections)
      firstRequest.await()
      assertNotRequested(secondRequest)
    }
  }

  @Nested
  inner class NoSelectionRangeCapability {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `no request when the server has no selection range capability`() = timeoutRunBlocking {
      assertWordSelectedWithoutRequest("select7.txt") { fakeLspIntegration.serverSession(it) }
    }
  }

  @Nested
  inner class ShouldAskServerForSelectionRangeFalse {
    private val fakeLspIntegration by selectionRangeIntegrationFixture(object : LspSelectionRangeSupport() {
      override fun shouldAskServerForSelectionRange(file: VirtualFile): Boolean = false
    })

    @Test
    fun `no request when the customizer declines the file`() = timeoutRunBlocking {
      assertWordSelectedWithoutRequest("select8.txt") { fakeLspIntegration.serverSession(it) }
    }
  }

  @Nested
  inner class SelectionRangeDisabled {
    private val fakeLspIntegration by selectionRangeIntegrationFixture(LspSelectionRangeDisabled)

    @Test
    fun `no request when the customizer disables the feature`() = timeoutRunBlocking {
      assertWordSelectedWithoutRequest("select9.txt") { fakeLspIntegration.serverSession(it) }
    }
  }

  private suspend fun assertWordSelectedWithoutRequest(
    fileName: String,
    serverSession: suspend (VirtualFile) -> FakeLspServerSession,
  ) {
    val virtualFile = codeInsightFixture.configureByText(fileName, TEXT).virtualFile
    val session = serverSession(virtualFile)
    val request = session.expectRequest(session.SELECTION_RANGE) { listOf(serverChain()) }

    val selection = extendSelection()

    assertEquals(WORD, selection)
    assertNotRequested(request)
  }

  private fun selectionRangeIntegrationFixture(customizer: LspSelectionRangeCustomizer = LspSelectionRangeSupport()) =
    projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val selectionRangeCustomizer: LspSelectionRangeCustomizer = customizer
      },
      configureServerCapabilities = {
        selectionRangeProvider = Either.forLeft(true)
      },
    )
}
