package com.intellij.platform.lsp

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.FoldRegion
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
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@TestApplication
internal class LspFoldingRangeTest {
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
      foldingRangeProvider = Either.forLeft(true)
    },
  )

  @BeforeEach
  fun setUp() {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
  }

  @Nested
  inner class BasicFolding {
    @Test
    fun `folding regions detected in document`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3\nline 4\nline 5").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(0, 2))
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(1) { regions ->
        val document = codeInsightFixture.editor.document
        assertEquals(0, document.getLineNumber(regions[0].startOffset))
        assertEquals(2, document.getLineNumber(regions[0].endOffset))
      }
    }
  }

  @Nested
  inner class CollapsedText {
    @Test
    fun `collapsed text shown for folded region`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(0, 2).apply { collapsedText = "..." })
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(1) { regions ->
        assertEquals("...", regions[0].placeholderText)
      }
    }

    @Test
    fun `default collapsed text when not specified`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(0, 2))  // No collapsedText set
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(1) { regions ->
        assertEquals("...", regions[0].placeholderText)
      }
    }
  }

  @Nested
  inner class RangeHandling {
    @Test
    fun `folding range with startLine and endLine only`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3\nline 4").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(1, 3))
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(1) { regions ->
        val document = codeInsightFixture.editor.document
        assertEquals(1, document.getLineNumber(regions[0].startOffset))
        assertEquals(3, document.getLineNumber(regions[0].endOffset))
      }
    }

    @Test
    fun `folding range with startCharacter and endCharacter`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(0, 2).apply {
          startCharacter = 3
          endCharacter = 4
        })
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(1)
    }
  }

  @Nested
  inner class NestedFolding {
    @Test
    fun `nested folding regions supported`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3\nline 4\nline 5").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(
          FoldingRange(0, 4),  // Outer fold
          FoldingRange(1, 3),  // Inner fold
        )
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(2)
    }
  }

  @Nested
  inner class EdgeCases {
    @Test
    fun `single line does not create folding region`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "line 1\nline 2\nline 3").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(1, 1))  // Single line range
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(0)
    }

    @Test
    fun `empty file returns no folding regions`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        emptyList()
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(0)
    }
  }

  private suspend fun checkFoldRegionsRetrying(
    expectedCount: Int,
    check: ((Array<FoldRegion>) -> Unit)? = null,
  ) {
    waitUntilAssertSucceeds(message = "Expected $expectedCount LSP fold regions") {
      readAction {
        val regions = codeInsightFixture.editor.foldingModel.allFoldRegions
        assertEquals(expectedCount, regions.size)
        check?.invoke(regions)
      }
    }
  }
}
