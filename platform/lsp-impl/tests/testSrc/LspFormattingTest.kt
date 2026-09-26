package com.intellij.platform.lsp

import com.intellij.core.CoreBundle
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Test

@TestApplication
class LspFormattingTest {
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
    lspCustomization = object : LspCustomization() {
      override val formattingCustomizer = object : LspFormattingSupport() {
        override fun shouldFormatThisFileExclusivelyByServer(
          file: VirtualFile,
          ideCanFormatThisFileItself: Boolean,
          serverExplicitlyWantsToFormatThisFile: Boolean,
        ): Boolean = true
      }
    },
    configureServerCapabilities = {
      documentFormattingProvider = Either.forLeft(true)
      documentRangeFormattingProvider = Either.forLeft(true)
    },
  )

  @Test
  fun `full file formatting`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Simple text file").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.FORMATTING, { it.textDocument.uri == fileUri }) {
      listOf(TextEdit(Range(Position(0, 6), Position(0, 7)), "\n"), TextEdit(Range(Position(0, 11), Position(0, 12)), "\n"))
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      CodeStyleManager.getInstance(project).reformat(codeInsightFixture.file)
    }

    serverSession.awaitExpected()
    codeInsightFixture.checkResult("Simple\ntext\nfile")
  }

  @Test
  fun `full file formatting with random TextEdits order`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Simple text file with multiple words").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.FORMATTING, { it.textDocument.uri == fileUri }) {
      listOf(
        TextEdit(Range(Position(0, 21), Position(0, 22)), "\n"),
        TextEdit(Range(Position(0, 11), Position(0, 12)), "\n"),
        TextEdit(Range(Position(0, 30), Position(0, 31)), "\n"),
        TextEdit(Range(Position(0, 6), Position(0, 7)), "\n"),
        TextEdit(Range(Position(0, 16), Position(0, 17)), "\n")
      )
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      CodeStyleManager.getInstance(project).reformat(codeInsightFixture.file)
    }

    serverSession.awaitExpected()
    codeInsightFixture.checkResult("Simple\ntext\nfile\nwith\nmultiple\nwords")
  }

  @Test
  fun `range formatting`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Line\nLine with 2 spaces\nLine").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri }) {
      listOf(TextEdit(Range(Position(1, 0), Position(1, 0)), "  "))
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      val document = codeInsightFixture.editor.document
      CodeStyleManager.getInstance(project)
        .reformatText(codeInsightFixture.file, document.getLineStartOffset(1), document.getLineEndOffset(1))
    }

    serverSession.awaitExpected()
    codeInsightFixture.checkResult("Line\n  Line with 2 spaces\nLine")
  }

  @Test
  fun `range formatting with multiple ranges`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Line with 2 spaces\nLine\nLine with tab\nAnother line\nMore tabs to the God of Tabs").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri && it.range.start.line == 2 }) {
      listOf(TextEdit(Range(Position(2, 0), Position(2, 0)), "\t"))
    }
    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri && it.range.start.line == 0 }) {
      listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "  "))
    }
    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri && it.range.start.line == 4 }) {
      listOf(TextEdit(Range(Position(4, 0), Position(4, 0)), "\t\t"))
    }
    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri && it.range.start.line == 3 }) {
      listOf(TextEdit(Range(Position(3, 0), Position(3, 0)), "> "))
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      val document = codeInsightFixture.editor.document
      val ranges = listOf(
        TextRange(document.getLineStartOffset(2), document.getLineEndOffset(2)),
        TextRange(document.getLineStartOffset(0), document.getLineEndOffset(0)),
        TextRange(document.getLineStartOffset(3), document.getLineEndOffset(3)),
        TextRange(document.getLineStartOffset(4), document.getLineEndOffset(4)),
      )
      CodeStyleManager.getInstance(project).reformatText(codeInsightFixture.file, ranges)
    }

    serverSession.awaitExpected()
    codeInsightFixture.checkResult("  Line with 2 spaces\nLine\n\tLine with tab\n> Another line\n\t\tMore tabs to the God of Tabs")
  }

  @Test
  fun `range formatting with range out of file length`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Line 1\nLine 2").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri }) {
      listOf(TextEdit(Range(Position(10, 0), Position(10, 5)), "Invalid"))
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      val document = codeInsightFixture.editor.document
      CodeStyleManager.getInstance(project)
        .reformatText(codeInsightFixture.file, document.getLineStartOffset(0), document.getLineEndOffset(0))
    }

    serverSession.awaitExpected()
    // Should remain unchanged because TextEdit was out of range
    codeInsightFixture.checkResult("Line 1\nLine 2")
  }

  @Test
  fun `range formatting with overlapping ranges`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "1234567890").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri }) {
      listOf(
        TextEdit(Range(Position(0, 0), Position(0, 5)), "ABC"),
        TextEdit(Range(Position(0, 3), Position(0, 7)), "DEF")
      )
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      val document = codeInsightFixture.editor.document
      CodeStyleManager.getInstance(project)
        .reformatText(codeInsightFixture.file, 0, document.textLength - 1)
    }

    serverSession.awaitExpected()
    // Overlapping edits result in a deterministic but often incorrect document state.
    codeInsightFixture.checkResult("ABCF890")
  }

  @Test
  fun `range formatting with multiple edits where some are out of range`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Line 1\nLine 2").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.RANGE_FORMATTING, { it.textDocument.uri == fileUri }) {
      listOf(
        TextEdit(Range(Position(1, 0), Position(1, 4)), "Modified"),
        TextEdit(Range(Position(10, 0), Position(10, 5)), "Invalid")
      )
    }

    writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
      val document = codeInsightFixture.editor.document
      CodeStyleManager.getInstance(project)
        .reformatText(codeInsightFixture.file, 0, document.textLength - 1)
    }

    serverSession.awaitExpected()
    // If any edit is invalid (out of bounds), the whole formatting task currently fails or stops.
    codeInsightFixture.checkResult("Line 1\nLine 2")
  }
}