// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
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
  override fun acceptsUrl(url: String, notebookSupported: Boolean): Boolean {
    return true
  }

  override fun acceptsFile(file: VirtualFile, notebookSupported: Boolean): Boolean {
    return true
  }

  override fun toLspDocumentPosition(lspServer: LspServer, file: VirtualFile, document: Document, hostOffset: Int): LspDocumentPosition? {
    val uri = lspServer.descriptor.getFileUri(file)
    return LspDocumentPosition(
      DefaultLspDocument(TextDocumentIdentifier(uri), uri),
      getLsp4jPosition(document, hostOffset)
    )
  }

  override fun toLspDocumentRangeSync(lspServer: LspServer, file: VirtualFile, document: Document, range: Range): List<LspDocumentRange> =
    lspDocumentRange(lspServer, file, range)

  override suspend fun toLspDocumentRange(lspServer: LspServer, file: VirtualFile, range: Range): List<LspDocumentRange> =
    lspDocumentRange(lspServer, file, range)

  private fun lspDocumentRange(lspServer: LspServer, file: VirtualFile, range: Range): List<LspDocumentRange> {
    val uri = lspServer.descriptor.getFileUri(file)
    return listOf(LspDocumentRange(DefaultLspDocument(TextDocumentIdentifier(uri), uri), range))
  }

  override fun toLspDocumentsInFileSync(lspServer: LspServer, file: VirtualFile): List<LspDocument> =
    lspDocument(lspServer, file)

  override suspend fun toLspDocumentsInFile(lspServer: LspServer, file: VirtualFile): List<LspDocument> =
    lspDocument(lspServer, file)

  private fun lspDocument(lspServer: LspServer, file: VirtualFile): List<LspDocument> {
    val uri = lspServer.descriptor.getFileUri(file)
    return listOf(DefaultLspDocument(TextDocumentIdentifier(uri), uri))
  }

  override fun sendDidOpen(
    lspServer: LspServer,
    file: VirtualFile,
    document: Document,
  ) {
    val languageId = lspServer.descriptor.getLanguageId(file)
    val fileUri = lspServer.descriptor.getFileUri(file)
    val version = (document as? DocumentEx)?.modificationSequence ?: document.modificationStamp.toInt()
    val item = TextDocumentItem(fileUri, languageId, version, document.text)
    lspServer.sendNotification { lsp4jServer ->
      lsp4jServer.textDocumentService.didOpen(DidOpenTextDocumentParams(item))
    }
  }

  override fun sendDidClose(
    lspServer: LspServer,
    file: VirtualFile,
    document: Document,
  ) {
    val fileUri = lspServer.descriptor.getFileUri(file)
    val params = DidCloseTextDocumentParams(TextDocumentIdentifier(fileUri))
    lspServer.sendNotification { lsp4jServer ->
      lsp4jServer.textDocumentService.didClose(params)
    }
  }

  override fun sendDidChangeFull(
    lspServer: LspServer,
    file: VirtualFile,
    document: Document,
  ) {
    val params = LspDidChangeUtil.createFullDidChangeParams(lspServer, document, file)
    lspServer.sendNotification { lsp4jServer ->
      lsp4jServer.textDocumentService.didChange(params)
    }
  }

  override fun sendDidChangeIncremental(
    lspServer: LspServer,
    file: VirtualFile,
    event: DocumentEvent,
  ) {
    val params = LspDidChangeUtil.createIncrementalDidChangeParamsBeforeDocumentChange(lspServer, event, file)
    lspServer.sendNotification { lsp4jServer ->
      lsp4jServer.textDocumentService.didChange(params)
    }
  }

  override fun sendDidSave(
    lspServer: LspServer,
    file: VirtualFile,
    document: Document,
    includeText: Boolean,
  ) {
    val fileUri = lspServer.descriptor.getFileUri(file)
    val documentIdentifier = TextDocumentIdentifier(fileUri)
    val text = if (includeText) document.text else null
    val params = DidSaveTextDocumentParams(documentIdentifier, text)
    lspServer.sendNotification { lsp4jServer ->
      lsp4jServer.textDocumentService.didSave(params)
    }
  }

  override fun getLspDocumentByUrl(
    lspServer: LspServer,
    targetUri: String,
  ): LspDocument? {
    return lspServer.descriptor.findFileByUri(targetUri)?.let {
      DefaultLspDocument(TextDocumentIdentifier(targetUri), targetUri)
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
