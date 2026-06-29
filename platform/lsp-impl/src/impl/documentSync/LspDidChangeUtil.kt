package com.intellij.platform.lsp.impl.documentSync

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LspDidChangeUtil {
  fun getFileToHandle(event: DocumentEvent): VirtualFile? =
    FileDocumentManager.getInstance().getFile(event.document)?.takeIf {
      it.isInLocalFileSystem && !StringUtil.equals(event.oldFragment, event.newFragment)
    }

  /**
  * At the moment of this function call the [documentEvent] must be not yet applied to the document,
  * which effectively means that this function must be called from [com.intellij.openapi.editor.event.DocumentListener.beforeDocumentChange].
  * This is needed to calculate line numbers in the document as they were *before* the change.
  */
  @RequiresEdt
  fun createIncrementalDidChangeParamsBeforeDocumentChange(
    lspClient: LspClient,
    documentEvent: DocumentEvent,
    virtualFile: VirtualFile,
  ): DidChangeTextDocumentParams {
    val versionedIdentifier = getVersionedIdentifier(lspClient, documentEvent.document, virtualFile)
    // This function is called at the moment when the `documentEvent` is not yet applied to the document,
    // but the state of the document that this `DidChangeTextDocumentParams` reports to the LSP server
    // assumes that the documentEvent has been applied.
    // If we don't increase the document version number, the LSP server will have the right to ignore the `didChange` notification
    // because the document version number may appear to be equal to the one sent previously within the `didOpen` request.
    versionedIdentifier.version = versionedIdentifier.version!! + 1

    val range = getLsp4jRange(documentEvent.document, documentEvent.offset, documentEvent.oldLength)
    val text = documentEvent.newFragment.toString()
    val changeEvent = TextDocumentContentChangeEvent(range, text)

    return DidChangeTextDocumentParams(versionedIdentifier, listOf(changeEvent))
  }

  @RequiresReadLock
  internal fun createFullDidChangeParams(
    lspClient: LspClient,
    document: Document,
    virtualFile: VirtualFile,
  ): DidChangeTextDocumentParams =
    DidChangeTextDocumentParams(
      getVersionedIdentifier(lspClient, document, virtualFile),
      listOf(TextDocumentContentChangeEvent(document.text))
    )

  private fun getVersionedIdentifier(
    lspClient: LspClient,
    document: Document,
    virtualFile: VirtualFile,
  ): VersionedTextDocumentIdentifier =
    VersionedTextDocumentIdentifier(
      lspClient.descriptor.getFileUri(virtualFile),
      lspClient.getDocumentVersion(document)
    )
}