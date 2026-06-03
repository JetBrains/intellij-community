// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.jetbrains.annotations.ApiStatus

/**
 * Maps between IntelliJ file/offset coordinates and LSP document/position coordinates.
 *
 * For regular files, this is a 1:1 mapping. For notebooks, it resolves which cell
 * the offset/range falls into and converts to cell-relative coordinates.
 *
 * Manages adapter lookup and provides helper methods used by [LspRequestExecutor]
 * to iterate over LSP documents within a file.
 */
@ApiStatus.Internal
class LspDocumentMapping(private val lspClient: LspClientImpl) {

  private val defaultAdapter: LspDocumentAdapter by lazy {
    DefaultLspDocumentAdapter()
  }

  fun getAdapterForFile(file: VirtualFile): LspDocumentAdapter {
    val notebookSupported = isNotebookSupportedByServer()
    val extensions = LspDocumentAdapter.EP_NAME.extensionList
    return extensions.firstOrNull { it.acceptsFile(file, notebookSupported) }
           ?: defaultAdapter
  }

  fun getAdapterForUrl(url: String): LspDocumentAdapter {
    val notebookSupported = isNotebookSupportedByServer()
    val extensions = LspDocumentAdapter.EP_NAME.extensionList
    return extensions.firstOrNull { it.acceptsUrl(url, notebookSupported) }
           ?: defaultAdapter
  }

  fun findDocumentByUrl(targetUri: String): LspDocument? {
    return getAdapterForUrl(targetUri).getLspDocumentByUrl(lspClient, targetUri)
  }

  fun getDocumentPosition(file: VirtualFile, document: Document, offset: Int): LspDocumentPosition? {
    return getAdapterForFile(file).toLspDocumentPosition(lspClient, file, document, offset)
  }

  @RequiresReadLock
  fun getDocumentRangesSync(file: VirtualFile, document: Document, range: Range): List<LspDocumentRange> {
    return getAdapterForFile(file).toLspDocumentRangeSync(lspClient, file, document, range)
  }

  fun getDocumentsInFileSync(file: VirtualFile): List<LspDocument> {
    return getAdapterForFile(file).toLspDocumentsInFileSync(lspClient, file)
  }

  /**
   * Resolves which LSP document the [offset] falls into and invokes [block]
   * with the document and the position within it.
   *
   * For regular files, the document covers the whole file.
   * For notebooks, it resolves to the correct cell.
   */
  fun <T> withDocumentAtOffset(file: VirtualFile, document: Document, offset: Int, block: (LspDocument, Position) -> T): T? {
    return getDocumentPosition(file, document, offset)?.let { block(it.document, it.position) }
  }

  /**
   * Like [withDocumentAtOffset], but also unwraps language injections.
   *
   * If [file] is a [VirtualFileWindow][com.intellij.injected.editor.VirtualFileWindow] (injected fragment),
   * the offset is translated to the host file before adapter resolution.
   *
   * @param file might be an injected VirtualFileWindow
   * @param offset might be an offset in the injected file
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun <T> withDocumentAtFileOffset(file: VirtualFile, offset: Int, block: (LspDocument, Position) -> T): T? {
    val host = unwrapInjection(file, offset) ?: return null
    return withDocumentAtOffset(host.hostFile, host.hostDocument, host.hostOffset, block)
  }

  suspend fun <T> forEachDocumentInFile(file: VirtualFile, block: suspend (LspDocument) -> T): List<T> {
    return getAdapterForFile(file).toLspDocumentsInFile(lspClient, file).map { block(it) }
  }

  suspend fun <T> forEachDocumentInFile(file: VirtualFile, block: suspend (LspDocument, Range) -> T): List<T> {
    val range = readAction {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
      getLsp4jRange(document, 0, document.textLength)
    } ?: return emptyList()
    return getAdapterForFile(file).toLspDocumentRange(lspClient, file, range).map { block(it.document, it.range) }
  }


  /**
   * Unwraps language injection, returning the host file, document, and offset.
   * If [file] is not injected, returns it as-is.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun unwrapInjection(file: VirtualFile, offset: Int): HostCoordinates? {
    ProgressManager.checkCanceled()
    val document = FileDocumentManager.getInstance().getDocument(file)
                   ?: return null.also { lspClient.logError("No document for file $file") }
    val hostFile = (file as? VirtualFileWindow)?.delegate ?: file
    val hostDocument = PsiDocumentManagerBase.getTopLevelDocument(document)
    val hostOffset = (document as? DocumentWindow)?.injectedToHost(offset) ?: offset
    return HostCoordinates(hostFile, hostDocument, hostOffset)
  }

  class HostCoordinates(val hostFile: VirtualFile, val hostDocument: Document, val hostOffset: Int)

  private fun isNotebookSupportedByServer(): Boolean {
    return lspClient.serverCapabilities?.notebookDocumentSync != null
  }
}

/**
 * Aggregates a per-document result list (typically produced with a block that returns `null` to signal a missing/failed response).
 *
 * Returns `null` only when **every** entry is `null` — that means no document produced a response,
 * and the cahe should keep its previous state.
 */
internal fun <T> List<List<T>?>.aggregatePerDocumentResults(): List<T>? {
  if (all { it == null }) return null
  return filterNotNull().flatten()
}

/**
 * Internal accessor for [LspDocumentMapping] from [LspClient] references in internal code.
 */
internal val LspClient.documentMapping: LspDocumentMapping
  get() = (this as LspClientImpl).documentMapping
