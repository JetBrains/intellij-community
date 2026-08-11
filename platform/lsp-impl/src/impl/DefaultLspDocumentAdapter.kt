// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.impl.documentSync.LspDidChangeUtil
import com.intellij.platform.lsp.util.getLsp4jPosition
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.jetbrains.annotations.ApiStatus

/**
 * Default implementation of [LspDocumentAdapter] that works with regular text documents.
 * Does not handle notebook cell-relative positions.
 */
@ApiStatus.Internal
open class DefaultLspDocumentAdapter : LspDocumentAdapter {
  override fun acceptsUrl(lspClient: LspClient, url: String): Boolean {
    return true
  }

  override fun acceptsFile(lspClient: LspClient, file: VirtualFile): Boolean {
    return true
  }

  override fun toLspDocumentPosition(lspClient: LspClient, file: VirtualFile, document: Document, hostOffset: Int): LspDocumentPosition? {
    val uri = lspClient.descriptor.getFileUri(file)
    return LspDocumentPosition(
      DefaultLspDocument(TextDocumentIdentifier(uri), uri),
      getLsp4jPosition(document, hostOffset)
    )
  }

  override fun toLspDocumentRangeSync(lspClient: LspClient, file: VirtualFile, document: Document, range: Range): List<LspDocumentRange> =
    lspDocumentRange(lspClient, file, range)

  override suspend fun toLspDocumentRange(lspClient: LspClient, file: VirtualFile, range: Range): List<LspDocumentRange> =
    lspDocumentRange(lspClient, file, range)

  private fun lspDocumentRange(lspClient: LspClient, file: VirtualFile, range: Range): List<LspDocumentRange> {
    val uri = lspClient.descriptor.getFileUri(file)
    return listOf(LspDocumentRange(DefaultLspDocument(TextDocumentIdentifier(uri), uri), range))
  }

  override fun toLspDocumentsInFileSync(lspClient: LspClient, file: VirtualFile): List<LspDocument> =
    lspDocument(lspClient, file)

  override suspend fun toLspDocumentsInFile(lspClient: LspClient, file: VirtualFile): List<LspDocument> =
    lspDocument(lspClient, file)

  private fun lspDocument(lspClient: LspClient, file: VirtualFile): List<LspDocument> {
    val uri = lspClient.descriptor.getFileUri(file)
    return listOf(DefaultLspDocument(TextDocumentIdentifier(uri), uri))
  }

  override fun getLspDocumentByUrl(lspClient: LspClient, targetUri: String): LspDocument? {
    return lspClient.descriptor.findFileByUri(targetUri)?.let {
      DefaultLspDocument(TextDocumentIdentifier(targetUri), targetUri)
    }
  }

  override fun sendDidOpen(lspClient: LspClient, file: VirtualFile, document: Document) {
    val languageId = lspClient.descriptor.getLanguageId(file)
    val fileUri = lspClient.descriptor.getFileUri(file)
    val version = (document as? DocumentEx)?.modificationSequence ?: document.modificationStamp.toInt()
    val item = TextDocumentItem(fileUri, languageId, version, document.text)
    lspClient.sendNotification {
      it.textDocumentService.didOpen(DidOpenTextDocumentParams(item))
    }
  }

  override fun sendDidClose(lspClient: LspClient, file: VirtualFile, document: Document) {
    val fileUri = lspClient.descriptor.getFileUri(file)
    val params = DidCloseTextDocumentParams(TextDocumentIdentifier(fileUri))
    lspClient.sendNotification {
      it.textDocumentService.didClose(params)
    }
  }

  override fun sendDidChangeFull(lspClient: LspClient, file: VirtualFile, document: Document) {
    val params = LspDidChangeUtil.createFullDidChangeParams(lspClient, document, file)
    lspClient.sendNotification {
      it.textDocumentService.didChange(params)
    }
  }

  override fun sendDidChangeIncremental(lspClient: LspClient, file: VirtualFile, event: DocumentEvent) {
    val params = LspDidChangeUtil.createIncrementalDidChangeParamsBeforeDocumentChange(lspClient, event, file)
    lspClient.sendNotification {
      it.textDocumentService.didChange(params)
    }
  }

  override fun sendDidSave(lspClient: LspClient, file: VirtualFile, document: Document, includeText: Boolean) {
    val fileUri = lspClient.descriptor.getFileUri(file)
    val documentIdentifier = TextDocumentIdentifier(fileUri)
    val text = if (includeText) document.text else null
    val params = DidSaveTextDocumentParams(documentIdentifier, text)
    lspClient.sendNotification {
      it.textDocumentService.didSave(params)
    }
  }

  private class DefaultLspDocument(override val id: TextDocumentIdentifier, override val fileUri: String) : LspDocument {
    override fun toHostRange(range: Range): Range = range
    override fun toHostPosition(position: Position): Position = position
    override fun toHostLine(line: Int): Int = line
    override fun <T> mapToHost(input: T, transform: LspDocument.(T) -> T): T = input
    override fun prepareDocument(hostDocument: Document, build: (Document) -> Document?): Document = hostDocument
  }

}
