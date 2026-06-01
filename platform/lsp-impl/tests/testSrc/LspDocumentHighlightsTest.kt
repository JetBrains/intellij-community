// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingResult
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
internal class LspDocumentHighlightsTest {
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
      documentHighlightProvider = Either.forLeft(true)
    },
  )

  private suspend fun triggerHighlightUsages(): IdentifierHighlightingResult {
    return readAction {
      CodeInsightTestUtil.runIdentifierHighlighterPass(codeInsightFixture.file, codeInsightFixture.editor)
    }
  }

  private fun IdentifierHighlightingResult.toHighlightedRanges(): List<HighlightedRange> {
    return occurrences
      .map {
        HighlightedRange(TextRange(it.range.startOffset, it.range.endOffset),
                         it.highlightInfoType == HighlightInfoType.ELEMENT_UNDER_CARET_WRITE)
      }
      .sortedBy { it.range.startOffset }
  }

  private data class HighlightedRange(val range: TextRange, val isWrite: Boolean)

  @Nested
  inner class BasicHighlighting {
    @Test
    fun `given caret on symbol when highlight usages triggered then all occurrences are highlighted`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar foo baz foo").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri == fileUri && it.position == Position(0, 0) }) {
        listOf(
          DocumentHighlight(Range(Position(0, 0), Position(0, 3)), DocumentHighlightKind.Read),
          DocumentHighlight(Range(Position(0, 8), Position(0, 11)), DocumentHighlightKind.Read),
          DocumentHighlight(Range(Position(0, 16), Position(0, 19)), DocumentHighlightKind.Read),
        )
      }

      // when
      val result = triggerHighlightUsages()

      // then
      serverSession.awaitExpected()
      val ranges = result.toHighlightedRanges()
      assertEquals(3, ranges.size, "Expected 3 highlighted ranges")
      assertEquals(TextRange(0, 3), ranges[0].range)
      assertEquals(TextRange(8, 11), ranges[1].range)
      assertEquals(TextRange(16, 19), ranges[2].range)
    }

    @Test
    fun `given caret on symbol with no usages when highlight usages triggered then no highlights are added`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri == fileUri && it.position == Position(0, 0) }) {
        emptyList()
      }

      // when
      val result = triggerHighlightUsages()

      // then
      serverSession.awaitExpected()
      val ranges = result.toHighlightedRanges()
      assertTrue(ranges.isEmpty(), "Expected no highlighted ranges")
    }
  }

  @Nested
  inner class ReadWriteHighlighting {
    @Test
    fun `given caret on symbol with read and write usages when highlight usages triggered then usages are distinguished by kind`() =
      timeoutRunBlocking {
        // given
        val text = """
        val <caret>x = 1
        print(x)
        x = 2
      """.trimIndent()
        val virtualFile = codeInsightFixture.configureByText("test.txt", text).virtualFile
        val serverSession = configureServerSession(project, virtualFile)
        val fileUri = serverSession.fileUri(virtualFile)

        serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri == fileUri && it.position == Position(0, 4) }) {
          listOf(
            DocumentHighlight(Range(Position(0, 4), Position(0, 5)), DocumentHighlightKind.Write),
            DocumentHighlight(Range(Position(1, 6), Position(1, 7)), DocumentHighlightKind.Read),
            DocumentHighlight(Range(Position(2, 0), Position(2, 1)), DocumentHighlightKind.Write),
          )
        }

        // when
        val result = triggerHighlightUsages()

        // then
        serverSession.awaitExpected()
        val ranges = result.toHighlightedRanges()
        assertEquals(3, ranges.size, "Expected 3 highlighted ranges")

        assertTrue(ranges[0].isWrite, "The first occurrence should be a write")
        assertEquals(TextRange(4, 5), ranges[0].range)

        assertFalse(ranges[1].isWrite, "The second occurrence should be a read")
        assertEquals(TextRange(16, 17), ranges[1].range)

        assertTrue(ranges[2].isWrite, "The third occurrence should be a write")
        assertEquals(TextRange(19, 20), ranges[2].range)
      }

    @Test
    fun `given caret on symbol with text kind when highlight usages triggered then treated as read`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar foo").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri == fileUri && it.position == Position(0, 0) }) {
        listOf(
          DocumentHighlight(Range(Position(0, 0), Position(0, 3)), DocumentHighlightKind.Text),
          DocumentHighlight(Range(Position(0, 8), Position(0, 11)), DocumentHighlightKind.Text),
        )
      }

      // when
      val result = triggerHighlightUsages()

      // then
      serverSession.awaitExpected()
      val ranges = result.toHighlightedRanges()
      assertEquals(2, ranges.size, "Expected 2 highlighted ranges")

      assertFalse(ranges[0].isWrite, "Text kind should be treated as read")
      assertEquals(TextRange(0, 3), ranges[0].range)

      assertFalse(ranges[1].isWrite, "Text kind should be treated as read")
      assertEquals(TextRange(8, 11), ranges[1].range)
    }

    @Test
    fun `given caret on symbol with no kind specified when highlight usages triggered then treated as read`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar foo").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri == fileUri && it.position == Position(0, 0) }) {
        listOf(
          DocumentHighlight(Range(Position(0, 0), Position(0, 3)), null),
          DocumentHighlight(Range(Position(0, 8), Position(0, 11)), null),
        )
      }

      // when
      val result = triggerHighlightUsages()

      // then
      serverSession.awaitExpected()
      val ranges = result.toHighlightedRanges()
      assertEquals(2, ranges.size, "Expected 2 highlighted ranges")

      assertFalse(ranges[0].isWrite, "Null kind should be treated as read")
      assertEquals(TextRange(0, 3), ranges[0].range)

      assertFalse(ranges[1].isWrite, "Null kind should be treated as read")
      assertEquals(TextRange(8, 11), ranges[1].range)
    }
  }

  @Nested
  inner class MultilineHighlighting {
    @Test
    fun `given caret on symbol in multiline document when highlight usages triggered then correct ranges are highlighted`() =
      timeoutRunBlocking {
        // given
        val text = """
        function <caret>foo() {
          foo()
          return foo
        }
      """.trimIndent()
        val virtualFile = codeInsightFixture.configureByText("test.txt", text).virtualFile
        val serverSession = configureServerSession(project, virtualFile)
        val fileUri = serverSession.fileUri(virtualFile)

        serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri == fileUri && it.position == Position(0, 9) }) {
          listOf(
            DocumentHighlight(Range(Position(0, 9), Position(0, 12)), DocumentHighlightKind.Write),
            DocumentHighlight(Range(Position(1, 2), Position(1, 5)), DocumentHighlightKind.Read),
            DocumentHighlight(Range(Position(2, 9), Position(2, 12)), DocumentHighlightKind.Read),
          )
        }

        // when
        val result = triggerHighlightUsages()

        // then
        serverSession.awaitExpected()
        val ranges = result.toHighlightedRanges()
        assertEquals(3, ranges.size, "Expected 3 highlighted ranges")

        assertEquals(TextRange(9, 12), ranges[0].range, "The first range should contain 'foo'")
        assertEquals(TextRange(19, 22), ranges[1].range, "The second range should contain 'foo'")
        assertEquals(TextRange(34, 37), ranges[2].range, "The third range should contain 'foo'")
      }
  }
}
