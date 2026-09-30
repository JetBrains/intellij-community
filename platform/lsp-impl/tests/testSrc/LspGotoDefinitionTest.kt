// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.getCtrlMouseData
import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspHoverCustomizer
import com.intellij.platform.lsp.api.customization.LspHoverDisabled
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [textDocument/definition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_definition)
 * behind [Go To Declaration][com.intellij.codeInsight.navigation.actions.GotoDeclarationAction].
 */
@TestApplication
internal class LspGotoDefinitionTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** `bar` at offsets 4..7 refers to `foo` at offsets 0..3. */
    private const val TEXT: String = "foo b<caret>ar"
    private val DEFINITION_RANGE: Range = Range(Position(0, 0), Position(0, 3))
    private val REFERENCE_RANGE: Range = Range(Position(0, 4), Position(0, 7))
    private const val OFFSET_IN_REFERENCE: Int = 5
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  /**
   * Each nested class declares its own: the fixture stores [lspCustomization] in the shared project,
   * so a second one active in the same test would replace it.
   */
  private fun definitionIntegrationFixture(lspCustomization: LspCustomization = LspCustomization()) =
    projectFixture.fakeLspIntegrationFixture(
      lspCustomization = lspCustomization,
      configureServerCapabilities = {
        definitionProvider = Either.forLeft(true)
      },
    )

  private fun FakeLspServerSession.expectDefinition(fileUri: String, locationLinks: List<LocationLink>) =
    expectRequest(DEFINITION, { it.textDocument.uri == fileUri && it.position == Position(0, OFFSET_IN_REFERENCE) }) {
      Either.forRight(locationLinks)
    }

  private fun definitionInThisFile(fileUri: String): List<LocationLink> =
    listOf(LocationLink(fileUri, DEFINITION_RANGE, DEFINITION_RANGE, REFERENCE_RANGE))

  private suspend fun ctrlHover(offset: Int): CtrlMouseData? = readAction {
    getCtrlMouseData(ACTION_GOTO_DECLARATION, codeInsightFixture.editor, codeInsightFixture.file, offset)
  }

  @Nested
  inner class GoToDeclarationAction {
    @Suppress("unused")
    private val fakeLspIntegration by definitionIntegrationFixture()

    @Test
    fun `given definition when go to declaration is invoked then the caret moves to the definition`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, definitionInThisFile(fileUri))

      withContext(Dispatchers.EDT) {
        codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
      }

      serverSession.awaitExpected()
      assertEquals(0, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
    }

    @Test
    fun `given no definition when go to declaration is invoked then the caret stays put`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, emptyList())

      withContext(Dispatchers.EDT) {
        codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
      }

      serverSession.awaitExpected()
      assertEquals(OFFSET_IN_REFERENCE, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
    }

    @Test
    fun `given a definition pointing at the reference itself when go to declaration is invoked then the caret stays put`() =
      timeoutRunBlocking {
        val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
        val serverSession = configureServerSession(project, virtualFile)
        val fileUri = serverSession.fileUri(virtualFile)
        serverSession.expectDefinition(fileUri, listOf(LocationLink(fileUri, REFERENCE_RANGE, REFERENCE_RANGE, REFERENCE_RANGE)))

        withContext(Dispatchers.EDT) {
          codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
        }

        serverSession.awaitExpected()
        assertEquals(OFFSET_IN_REFERENCE, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
      }
  }

  @Nested
  inner class CtrlHover {
    @Suppress("unused")
    private val fakeLspIntegration by definitionIntegrationFixture()

    @Test
    fun `given definition when hovering over the reference then its range is highlighted as a link`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, definitionInThisFile(fileUri))

      val data = ctrlHover(OFFSET_IN_REFERENCE)

      serverSession.awaitExpected()
      assertNotNull(data, "Expected Ctrl+hover data for a reference with a definition")
      assertEquals(listOf(TextRange(4, 7)), data!!.ranges)
      assertTrue(data.isNavigatable, "Expected the reference to be highlighted as a link")
    }

    @Test
    fun `given no definition when hovering over the reference then nothing is highlighted`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, emptyList())

      val data = ctrlHover(OFFSET_IN_REFERENCE)

      serverSession.awaitExpected()
      assertNull(data, "Expected no Ctrl+hover data when the server reports no definition")
    }

    @Test
    fun `given definition when hovering over the reference then there is no hint`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, definitionInThisFile(fileUri))

      val data = ctrlHover(OFFSET_IN_REFERENCE)

      serverSession.awaitExpected()
      assertNotNull(data, "Expected Ctrl+hover data for a reference with a definition")
      assertNull(data!!.hintText, "Expected no hint until the server's hover content can be shown")
    }

    @Test
    fun `given the pointer moves within the reference range then the server is asked once`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      // a single expectation: a second request would go unanswered and produce no data
      serverSession.expectRequest(serverSession.DEFINITION, { it.textDocument.uri == fileUri }) {
        Either.forRight(definitionInThisFile(fileUri))
      }

      val firstData = ctrlHover(4)
      val secondData = ctrlHover(6)

      serverSession.awaitExpected()
      assertEquals(listOf(TextRange(4, 7)), firstData?.ranges)
      assertEquals(listOf(TextRange(4, 7)), secondData?.ranges, "Expected the cached definition to be reused")
    }
  }

  @Nested
  inner class HoverDisabledByCustomizer {
    @Suppress("unused")
    private val fakeLspIntegration by definitionIntegrationFixture(object : LspCustomization() {
      override val hoverCustomizer: LspHoverCustomizer = LspHoverDisabled
    })

    @Test
    fun `given hover disabled when hovering over the reference then the server is not asked`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val definitionRequest = serverSession.expectDefinition(fileUri, definitionInThisFile(fileUri))

      val data = ctrlHover(OFFSET_IN_REFERENCE)

      assertNull(data, "Expected no Ctrl+hover data from a client that leaves hover to the IDE's own language support")
      assertFalse(definitionRequest.isCompleted, "Expected no textDocument/definition request on Ctrl+hover")
      // the test scope would otherwise keep waiting for the request
      definitionRequest.cancel()
    }

    @Test
    fun `given hover disabled when go to declaration is invoked then the caret moves to the definition`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, definitionInThisFile(fileUri))

      withContext(Dispatchers.EDT) {
        codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
      }

      serverSession.awaitExpected()
      assertEquals(0, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
    }
  }
}
