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
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@TestApplication
internal class LspDocumentColorTest {
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
      colorProvider = Either.forLeft(true)
    },
  )

  @Nested
  inner class BasicDocumentColors {
    @Test
    fun `colors detected in document`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "color: red"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(ColorInformation(Range(Position(0, 7), Position(0, 10)), Color(1.0, 0.0, 0.0, 1.0)))
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "color: /*<# <image> #>*/red")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `multiple colors detected in document`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "background: red; color: blue"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(
          ColorInformation(Range(Position(0, 12), Position(0, 15)), Color(1.0, 0.0, 0.0, 1.0)),
          ColorInformation(Range(Position(0, 24), Position(0, 28)), Color(0.0, 0.0, 1.0, 1.0)),
        )
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "background: /*<# <image> #>*/red; color: /*<# <image> #>*/blue")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }
  }

  private suspend fun checkInlaysRetrying(sourceText: String, expected: String) {
    waitUntilAssertSucceeds(message = "Inlays don't match expected") {
      codeInsightFixture.doHighlighting()
      assertEquals(expected.trim(), dumpInlays(sourceText).trim())
    }
  }

  @Test
  fun `unchanged color swatch reused across refresh, changed swatch recreated`(): Unit = timeoutRunBlocking {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    val sourceText = "red blue"
    val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { it.textDocument.uri == fileUri }) {
      listOf(
        ColorInformation(Range(Position(0, 0), Position(0, 3)), Color(1.0, 0.0, 0.0, 1.0)),
        ColorInformation(Range(Position(0, 4), Position(0, 8)), Color(0.0, 0.0, 1.0, 1.0)),
      )
    }
    checkInlaysRetrying(sourceText, "/*<# <image> #>*/red /*<# <image> #>*/blue")

    val redSwatchBefore = colorSwatchAt(0)
    val blueSwatchBefore = colorSwatchAt(4)

    // Second response: the red swatch is identical, the blue swatch changes to green.
    serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { it.textDocument.uri == fileUri }) {
      listOf(
        ColorInformation(Range(Position(0, 0), Position(0, 3)), Color(1.0, 0.0, 0.0, 1.0)),
        ColorInformation(Range(Position(0, 4), Position(0, 8)), Color(0.0, 1.0, 0.0, 1.0)),
      )
    }
    // Append at the end (does not shift the swatch offsets before it) to invalidate the cache so the pass re-requests.
    writeCommandAction(project, "") {
      codeInsightFixture.editor.document.insertString(sourceText.length, " x")
    }
    waitUntilAssertSucceeds(message = "Changed color swatch was not recreated") {
      codeInsightFixture.doHighlighting()
      assertNotSame(blueSwatchBefore, colorSwatchAt(4))
    }

    assertSame(redSwatchBefore, colorSwatchAt(0), "Unchanged color swatch must be reused, not recreated")

    serverSession.awaitExpected()
  }

  private fun dumpInlays(sourceText: String): String {
    return InlayDumpUtil.dumpInlays(
      sourceText,
      codeInsightFixture.editor,
      filter = null,
      renderer = { renderer: com.intellij.openapi.editor.EditorCustomElementRenderer, _: Inlay<*> -> renderer.toString() }
    )
  }

  private fun colorSwatchAt(offset: Int): Inlay<*> =
    codeInsightFixture.editor.inlayModel
      .getInlineElementsInRange(offset, offset, PresentationRenderer::class.java)
      .single()
}
