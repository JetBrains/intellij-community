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
    // This function is called at the moment when the `documentEvent` is not yet applied to the document, so
    // the version to declare can't be read off the document -- [LspClient.nextDocumentVersion] advances a
    // version counter this client owns outright instead, so there's nothing to predict.
    val versionedIdentifier = getVersionedIdentifier(lspClient, virtualFile, lspClient.nextDocumentVersion(documentEvent.document))

    val range = getLsp4jRange(documentEvent.document, documentEvent.offset, documentEvent.oldLength)
    val text = documentEvent.newFragment.toString()
    val changeEvent = TextDocumentContentChangeEvent(range, text)

    return DidChangeTextDocumentParams(versionedIdentifier, listOf(changeEvent))
  }

  @RequiresReadLock
  fun createFullDidChangeParams(
    lspClient: LspClient,
    document: Document,
    virtualFile: VirtualFile,
  ): DidChangeTextDocumentParams =
    DidChangeTextDocumentParams(
      getVersionedIdentifier(lspClient, virtualFile, lspClient.nextDocumentVersion(document)),
      listOf(TextDocumentContentChangeEvent(document.text))
    )

  private fun getVersionedIdentifier(
    lspClient: LspClient,
    virtualFile: VirtualFile,
    version: Int,
  ): VersionedTextDocumentIdentifier =
    VersionedTextDocumentIdentifier(lspClient.descriptor.getFileUri(virtualFile), version)
}