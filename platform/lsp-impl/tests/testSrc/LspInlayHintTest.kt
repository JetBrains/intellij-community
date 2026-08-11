package com.intellij.platform.lsp

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.command.writeCommandAction
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
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
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

  @Test
  fun `inlay hint with clickable label part`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
      listOf(InlayHint(Position(0, 5), Either.forRight(mutableListOf(
        InlayHintLabelPart("clickable").apply {
          location = Location(serverSession.fileUri(virtualFile), Range(Position(0, 0), Position(0, 0)))
        },
      ))))
    }

    launch(start = CoroutineStart.UNDISPATCHED) {
      checkInlaysRetrying(sourceText, "hello/*<# [clickable] #>*/ world")
    }

    codeInsightFixture.doHighlighting()
    serverSession.awaitExpected()
  }

  @Test
  fun `inlays removed when refresh returns no hints`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(InlayHint(Position(0, 5), Either.forLeft(": A")))
    }
    checkInlaysRetrying(sourceText, "hello/*<# : A #>*/ world")

    // The server now returns no hints; the existing inlay must be disposed (the diff's "dispose leftovers" path).
    // Append at the end to invalidate the cache so the pass re-requests.
    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      emptyList()
    }
    writeCommandAction(project, "") {
      codeInsightFixture.editor.document.insertString(sourceText.length, " x")
    }
    checkInlaysRetrying("hello world x", "hello world x")

    serverSession.awaitExpected()
  }

  @Test
  fun `two inlay hints at the same offset`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(
        InlayHint(Position(0, 5), Either.forLeft(": A")),
        InlayHint(Position(0, 5), Either.forLeft(": B")),
      )
    }
    waitUntilAssertSucceeds(message = "Both same-offset inlays should be present") {
      codeInsightFixture.doHighlighting()
      assertEquals(listOf(": A", ": B"), managedInlaysAt(5).map { it.renderer.toString() }.sorted())
    }

    val inlayABefore = managedInlaysAt(5).single { it.renderer.toString() == ": A" }

    // Second response: ": A" unchanged, the co-located ": B" changes to ": C".
    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(
        InlayHint(Position(0, 5), Either.forLeft(": A")),
        InlayHint(Position(0, 5), Either.forLeft(": C")),
      )
    }
    // Append at the end (does not shift offset 5) to invalidate the cache so the pass re-requests.
    writeCommandAction(project, "") {
      codeInsightFixture.editor.document.insertString(sourceText.length, " x")
    }
    waitUntilAssertSucceeds(message = "Co-located hint should be updated") {
      codeInsightFixture.doHighlighting()
      assertEquals(listOf(": A", ": C"), managedInlaysAt(5).map { it.renderer.toString() }.sorted())
    }

    assertSame(inlayABefore, managedInlaysAt(5).single { it.renderer.toString() == ": A" },
               "Unchanged co-located inlay must be reused, not recreated")

    serverSession.awaitExpected()
  }

  @Test
  fun `unchanged inlay reused across refresh, changed inlay recreated`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "foo bar baz"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(
        InlayHint(Position(0, 3), Either.forLeft(": Int")),
        InlayHint(Position(0, 7), Either.forLeft(": String")),
      )
    }
    checkInlaysRetrying(sourceText, "foo/*<# : Int #>*/ bar/*<# : String #>*/ baz")

    val intInlayBefore = managedInlayAt(3)
    val stringInlayBefore = managedInlayAt(7)

    // Second response: the ": Int" hint is identical, the ": String" hint changes to ": Long".
    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(
        InlayHint(Position(0, 3), Either.forLeft(": Int")),
        InlayHint(Position(0, 7), Either.forLeft(": Long")),
      )
    }
    // Append at the end (does not shift the hint offsets before it) to invalidate the cache so the pass re-requests.
    writeCommandAction(project, "") {
      codeInsightFixture.editor.document.insertString(sourceText.length, " x")
    }
    checkInlaysRetrying("foo bar baz x", "foo/*<# : Int #>*/ bar/*<# : Long #>*/ baz x")

    assertSame(intInlayBefore, managedInlayAt(3), "Unchanged inlay must be reused, not recreated")
    assertNotSame(stringInlayBefore, managedInlayAt(7), "Changed inlay must be recreated")

    serverSession.awaitExpected()
  }

  @Test
  fun `refresh re-requests hints without an edit`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "hello world"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(InlayHint(Position(0, 5), Either.forLeft(": A")))
    }
    checkInlaysRetrying(sourceText, "hello/*<# : A #>*/ world")

    // The server signals its hints changed even though the document didn't — expect a fresh request, no edit.
    serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
      listOf(InlayHint(Position(0, 5), Either.forLeft(": B")))
    }
    serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }

    checkInlaysRetrying(sourceText, "hello/*<# : B #>*/ world")
    serverSession.awaitExpected()
  }

  private suspend fun checkInlaysRetrying(sourceText: String, expected: String) {
    waitUntilAssertSucceeds(message = "Inlays don't match expected") {
      codeInsightFixture.doHighlighting()
      assertEquals(expected.trim(), dumpInlays(sourceText).trim())
    }
  }

  private fun managedInlayAt(offset: Int): Inlay<*> =
    codeInsightFixture.editor.inlayModel
      .getInlineElementsInRange(offset, offset, PresentationRenderer::class.java)
      .single()

  @Suppress("SameParameterValue")
  private fun managedInlaysAt(offset: Int): List<Inlay<*>> =
    codeInsightFixture.editor.inlayModel
      .getInlineElementsInRange(offset, offset, PresentationRenderer::class.java)

  private fun dumpInlays(sourceText: String): String {
    return InlayDumpUtil.dumpInlays(
      sourceText,
      codeInsightFixture.editor,
      filter = null,
      renderer = { renderer: com.intellij.openapi.editor.EditorCustomElementRenderer, _: Inlay<*> -> renderer.toString() }
    )
  }
}
