package com.intellij.platform.lsp

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.openapi.editor.Inlay
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.InlayHintRegistrationOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


@TestApplication
internal class LspInlayHintTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      inlayHintProvider = Either.forRight(InlayHintRegistrationOptions().apply {
        resolveProvider = true
      })
    },
  )

  @Test
  fun `basic inlay hint request`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
      listOf(InlayHint(Position(0, 5), Either.forLeft(": String")))
    }

    launch(start = CoroutineStart.UNDISPATCHED) {
      checkInlaysRetrying(sourceText, "hello/*<# : String #>*/ world")
    }

    codeInsightFixture.doHighlighting()
    serverSession.awaitExpected()
  }

  @Test
  fun `multiple inlay hints request`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "foo bar baz"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
      listOf(
        InlayHint(Position(0, 3), Either.forLeft(": Int")),
        InlayHint(Position(0, 7), Either.forLeft(": String")),
      )
    }

    launch(start = CoroutineStart.UNDISPATCHED) {
      checkInlaysRetrying(sourceText, "foo/*<# : Int #>*/ bar/*<# : String #>*/ baz")
    }

    codeInsightFixture.doHighlighting()
    serverSession.awaitExpected()
  }

  @Test
  fun `empty inlay hints request`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) { emptyList() }

    launch(start = CoroutineStart.UNDISPATCHED) {
      checkInlaysRetrying(sourceText, "hello world")
    }

    codeInsightFixture.doHighlighting()
    serverSession.awaitExpected()
  }

  @Test
  fun `inlay hint with composite label parts`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
      listOf(InlayHint(Position(0, 5), Either.forRight(mutableListOf(
        InlayHintLabelPart("hello"),
        InlayHintLabelPart("world"),
      ))))
    }

    launch(start = CoroutineStart.UNDISPATCHED) {
      checkInlaysRetrying(sourceText, "hello/*<# [hello world] #>*/ world")
    }

    codeInsightFixture.doHighlighting()
    serverSession.awaitExpected()
  }

  private suspend fun checkInlaysRetrying(sourceText: String, expected: String) {
    waitUntilAssertSucceeds(message = "Inlays don't match expected") {
      codeInsightFixture.doHighlighting()
      assertEquals(expected.trim(), dumpInlays(sourceText).trim())
    }
  }

  private fun dumpInlays(sourceText: String): String {
    return InlayDumpUtil.dumpInlays(
      sourceText,
      codeInsightFixture.editor,
      filter = null,
      renderer = { renderer: com.intellij.openapi.editor.EditorCustomElementRenderer, _: Inlay<*> -> renderer.toString() }
    )
  }
}
