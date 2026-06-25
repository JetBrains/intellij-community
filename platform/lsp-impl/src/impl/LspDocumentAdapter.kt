// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import org.eclipse.lsp4j.Range
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for converting between IntelliJ document positions and LSP positions.
 *
 * This interface is responsible for:
 * - Determining which documents this implementation handles
 * - Converting between IntelliJ document offsets and LSP document positions
 * - Handling document-specific position conversion (e.g., cell URIs and cell-relative positions for notebooks)
 *
 * Default implementation works with regular text documents.
 * Notebook implementations can provide their own converter by implementing this extension point.
 *
 * Extensions are checked in order of registration. The first extension that returns `true` from
 * [acceptsFile] will be used for position conversion.
 *
 * @see LspDocumentPosition
 */
@ApiStatus.Internal
interface LspDocumentAdapter {

  companion object {
    val EP_NAME: ExtensionPointName<LspDocumentAdapter> = ExtensionPointName.create("com.intellij.platform.lsp.documentAdapter")
  }

  fun acceptsUrl(lspClient: LspClient, url: String): Boolean
  fun acceptsFile(lspClient: LspClient, file: VirtualFile): Boolean
  fun toLspDocumentPosition(lspClient: LspClient, file: VirtualFile, document: Document, hostOffset: Int): LspDocumentPosition?
  fun toLspDocumentRangeSync(lspClient: LspClient, file: VirtualFile, document: Document, range: Range): List<LspDocumentRange>
  suspend fun toLspDocumentRange(lspClient: LspClient, file: VirtualFile, range: Range): List<LspDocumentRange>
  fun toLspDocumentsInFileSync(lspClient: LspClient, file: VirtualFile): List<LspDocument>
  suspend fun toLspDocumentsInFile(lspClient: LspClient, file: VirtualFile): List<LspDocument>
  fun getLspDocumentByUrl(lspClient: LspClient, targetUri: String): LspDocument?
  fun sendDidOpen(lspClient: LspClient, file: VirtualFile, document: Document)
  fun sendDidClose(lspClient: LspClient, file: VirtualFile, document: Document)
  fun sendDidChangeFull(lspClient: LspClient, file: VirtualFile, document: Document)
  fun sendDidChangeIncremental(lspClient: LspClient, file: VirtualFile, event: DocumentEvent)
  fun sendDidSave(lspClient: LspClient, file: VirtualFile, document: Document, includeText: Boolean)
}
