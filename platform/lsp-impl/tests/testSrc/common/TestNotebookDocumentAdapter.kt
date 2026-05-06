package com.intellij.platform.lsp.common

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.impl.LspDocument
import com.intellij.platform.lsp.impl.LspDocumentAdapter
import com.intellij.platform.lsp.impl.LspDocumentPosition
import com.intellij.platform.lsp.impl.LspDocumentRange
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams
import org.eclipse.lsp4j.NotebookCell
import org.eclipse.lsp4j.NotebookCellArrayChange
import org.eclipse.lsp4j.NotebookCellKind
import org.eclipse.lsp4j.NotebookDocument
import org.eclipse.lsp4j.NotebookDocumentChangeEvent
import org.eclipse.lsp4j.NotebookDocumentChangeEventCells
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellStructure
import org.eclipse.lsp4j.NotebookDocumentIdentifier
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedNotebookDocumentIdentifier
import java.net.URI

/**
 * Minimal notebook adapter for LSP protocol tests.
 * Splits content by `\n---\n` into cells addressed as `$fileUri#cell-$index`.
 *
 * Both [sendDidChangeFull] and [sendDidChangeIncremental] rebuild full cell structure —
 * this adapter does not produce true incremental cell text edits.
 * Tests using this adapter verify that the platform dispatches to the correct sync path,
 * not that the adapter itself produces incremental payloads.
 */
internal class TestNotebookDocumentAdapter : LspDocumentAdapter {
  companion object {
    private const val CELL_DELIMITER = "\n---\n"
  }

  override fun acceptsFile(file: VirtualFile, notebookSupported: Boolean): Boolean =
    notebookSupported && file.extension == "test-notebook"

  override fun acceptsUrl(url: String, notebookSupported: Boolean): Boolean {
    if (!notebookSupported) return false
    val fragment = URI.create(url).fragment ?: return false
    return fragment.startsWith("cell-")
  }

  override fun toLspDocumentPosition(lspServer: LspServer, file: VirtualFile, document: Document, hostOffset: Int): LspDocumentPosition? {
    val cells = document.text.split(CELL_DELIMITER)
    val fileUri = lspServer.descriptor.getFileUri(file)

    var currentOffset = 0
    for ((index, cellText) in cells.withIndex()) {
      val isLastCell = index == cells.size - 1
      if (hostOffset < currentOffset + cellText.length || (isLastCell && hostOffset == currentOffset + cellText.length)) {
        val cellOffset = (hostOffset - currentOffset).coerceAtMost(cellText.length)
        val cellDoc = DocumentImpl(cellText, true)
        val line = cellDoc.getLineNumber(cellOffset)
        val position = Position(line, cellOffset - cellDoc.getLineStartOffset(line))
        val cellUri = "$fileUri#cell-$index"
        val hostLineOffset = document.getLineNumber(currentOffset)
        return LspDocumentPosition(
          TestLspDocument(fileUri, TextDocumentIdentifier(cellUri), hostLineOffset),
          position
        )
      }
      currentOffset += cellText.length + CELL_DELIMITER.length
    }
    return null
  }

  override fun toLspDocumentsInFileSync(lspServer: LspServer, file: VirtualFile): List<LspDocument> =
    runReadActionBlocking { computeDocuments(lspServer, file) }

  override suspend fun toLspDocumentsInFile(lspServer: LspServer, file: VirtualFile): List<LspDocument> =
    readAction { computeDocuments(lspServer, file) }

  private fun computeDocuments(lspServer: LspServer, file: VirtualFile): List<LspDocument> {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
    val cells = document.text.split(CELL_DELIMITER)
    val fileUri = lspServer.descriptor.getFileUri(file)
    val result = mutableListOf<LspDocument>()
    var currentOffset = 0
    for ((index, cellText) in cells.withIndex()) {
      val cellUri = "$fileUri#cell-$index"
      val hostLineOffset = document.getLineNumber(currentOffset)
      result.add(TestLspDocument(fileUri, TextDocumentIdentifier(cellUri), hostLineOffset))
      currentOffset += cellText.length + CELL_DELIMITER.length
    }
    return result
  }

  override fun toLspDocumentRangeSync(lspServer: LspServer, file: VirtualFile, document: Document, range: Range): List<LspDocumentRange> =
    computeRanges(lspServer, file, document, range)

  override suspend fun toLspDocumentRange(lspServer: LspServer, file: VirtualFile, range: Range): List<LspDocumentRange> =
    readAction {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction emptyList()
      computeRanges(lspServer, file, document, range)
    }

  private fun computeRanges(lspServer: LspServer, file: VirtualFile, document: Document, range: Range): List<LspDocumentRange> {
    val cells = document.text.split(CELL_DELIMITER)
    val fileUri = lspServer.descriptor.getFileUri(file)

    val startOffset = computeOffset(document, range.start) ?: return emptyList()
    val endOffset = computeOffset(document, range.end) ?: return emptyList()

    val result = mutableListOf<LspDocumentRange>()
    var currentOffset = 0
    for ((index, cellText) in cells.withIndex()) {
      val cellStart = currentOffset
      val cellEnd = currentOffset + cellText.length
      if (cellStart < endOffset && cellEnd > startOffset) {
        val cellDoc = DocumentImpl(cellText, true)
        val s = maxOf(0, startOffset - cellStart)
        val e = minOf(cellText.length, endOffset - cellStart)
        val cellUri = "$fileUri#cell-$index"
        val hostLineOffset = document.getLineNumber(cellStart)
        result.add(LspDocumentRange(
          TestLspDocument(fileUri, TextDocumentIdentifier(cellUri), hostLineOffset),
          Range(computePosition(cellDoc, s), computePosition(cellDoc, e))
        ))
      }
      currentOffset += cellText.length + CELL_DELIMITER.length
    }
    return result
  }

  override fun sendDidOpen(lspServer: LspServer, file: VirtualFile, document: Document) {
    val cells = document.text.split(CELL_DELIMITER)
    val fileUri = lspServer.descriptor.getFileUri(file)
    val version = docVersion(document)
    val notebookDoc = NotebookDocument().apply {
      uri = fileUri
      notebookType = "test-notebook"
      this.version = version
      metadata = null
      this.cells = cells.mapIndexed { i, _ ->
        NotebookCell().apply { kind = NotebookCellKind.Code; this.document = "$fileUri#cell-$i" }
      }
    }
    val cellDocs = cells.mapIndexed { i, text ->
      TextDocumentItem().apply {
        uri = "$fileUri#cell-$i"; languageId = "plaintext"; this.version = version; this.text = text
      }
    }
    lspServer.sendNotification { it.notebookDocumentService.didOpen(DidOpenNotebookDocumentParams(notebookDoc, cellDocs)) }
  }

  override fun sendDidClose(lspServer: LspServer, file: VirtualFile, document: Document) {
    val cells = document.text.split(CELL_DELIMITER)
    val fileUri = lspServer.descriptor.getFileUri(file)
    val cellIds = cells.indices.map { TextDocumentIdentifier("$fileUri#cell-$it") }
    lspServer.sendNotification {
      it.notebookDocumentService.didClose(DidCloseNotebookDocumentParams(NotebookDocumentIdentifier(fileUri), cellIds))
    }
  }

  override fun sendDidChangeFull(lspServer: LspServer, file: VirtualFile, document: Document) =
    sendChangeNotification(lspServer, file, document)

  override fun sendDidChangeIncremental(lspServer: LspServer, file: VirtualFile, event: DocumentEvent) =
    sendChangeNotification(lspServer, file, event.document)

  override fun sendDidSave(lspServer: LspServer, file: VirtualFile, document: Document, includeText: Boolean) {
    val fileUri = lspServer.descriptor.getFileUri(file)
    lspServer.sendNotification {
      it.notebookDocumentService.didSave(DidSaveNotebookDocumentParams(NotebookDocumentIdentifier(fileUri)))
    }
  }

  override fun getLspDocumentByUrl(lspServer: LspServer, targetUri: String): LspDocument? {
    val uri = URI.create(targetUri)
    val fragment = uri.fragment ?: return null
    if (!fragment.startsWith("cell-")) return null
    val cellIndex = fragment.removePrefix("cell-").toIntOrNull() ?: return null

    val fileUri = targetUri.substringBefore("#")
    val file = lspServer.descriptor.findFileByUri(fileUri) ?: return null

    @Suppress("DEPRECATION")
    val hostLineOffset = ReadAction.compute<Int?, Throwable> {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@compute null
      val cells = document.text.split(CELL_DELIMITER)
      if (cellIndex !in cells.indices) return@compute null

      var offset = 0
      for (i in 0 until cellIndex) {
        offset += cells[i].length + CELL_DELIMITER.length
      }
      document.getLineNumber(offset)
    } ?: return null

    return TestLspDocument(fileUri, TextDocumentIdentifier(targetUri), hostLineOffset)
  }

  private fun sendChangeNotification(lspServer: LspServer, file: VirtualFile, document: Document) {
    val cells = document.text.split(CELL_DELIMITER)
    val fileUri = lspServer.descriptor.getFileUri(file)
    val version = docVersion(document)
    val id = VersionedNotebookDocumentIdentifier().apply { uri = fileUri; this.version = version }
    val cellChanges = NotebookDocumentChangeEventCells().apply {
      structure = NotebookDocumentChangeEventCellStructure().apply {
        array = NotebookCellArrayChange().apply {
          start = 0
          deleteCount = 0
          this.cells = cells.mapIndexed { i, _ ->
            NotebookCell().apply { kind = NotebookCellKind.Code; this.document = "$fileUri#cell-$i" }
          }
        }
        didOpen = cells.mapIndexed { i, text ->
          TextDocumentItem().apply {
            uri = "$fileUri#cell-$i"; languageId = "plaintext"; this.version = version; this.text = text
          }
        }
        didClose = null
      }
      textContent = null
      data = null
    }
    val event = NotebookDocumentChangeEvent().apply { metadata = null; this.cells = cellChanges }
    lspServer.sendNotification { it.notebookDocumentService.didChange(DidChangeNotebookDocumentParams(id, event)) }
  }

  private fun docVersion(document: Document): Int =
    (document as? DocumentEx)?.modificationSequence ?: document.modificationStamp.toInt()

  private fun computePosition(document: Document, offset: Int): Position {
    val line = document.getLineNumber(offset)
    return Position(line, offset - document.getLineStartOffset(line))
  }

  private fun computeOffset(document: Document, position: Position): Int? {
    if (position.line == document.lineCount && position.character == 0) return document.textLength
    if (position.line !in 0 until document.lineCount || position.character < 0) return null
    return (document.getLineStartOffset(position.line) + position.character).let { if (it <= document.textLength) it else null }
  }
}

internal class TestLspDocument(
  override val fileUri: String,
  override val id: TextDocumentIdentifier,
  private val hostLineOffset: Int = 0,
) : LspDocument {
  override fun toHostPosition(position: Position): Position = Position(position.line + hostLineOffset, position.character)
  override fun toHostRange(range: Range): Range = Range(toHostPosition(range.start), toHostPosition(range.end))
  override fun toHostLine(line: Int): Int = line + hostLineOffset
  override fun <T> mapToHost(input: T, transform: LspDocument.(T) -> T): T = transform(input)
}
