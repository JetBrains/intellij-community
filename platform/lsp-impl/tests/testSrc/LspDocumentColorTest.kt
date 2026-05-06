package com.intellij.platform.lsp

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.openapi.editor.Inlay
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.lspServerSupportFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
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
  private val lspServerSupport by projectFixture.lspServerSupportFixture(
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
    repeat(3) {
      codeInsightFixture.doHighlighting()
      delay(100)
      val actual = dumpInlays(sourceText)
      if (actual.trim() == expected.trim()) return
    }
    val actual = dumpInlays(sourceText)
    assertEquals(expected.trim(), actual.trim())
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
