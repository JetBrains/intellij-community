package com.intellij.platform.lsp.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

internal class LspDocumentListener : DocumentListener {
  private class ChangedFilesData {
    val handledFiles: MutableSet<VirtualFile> = VirtualFileSetFactory.getInstance().createCompactVirtualFileSet()
    val serversToSendDidOpen: MutableCollection<Pair<LspServerImpl, VirtualFile>> = mutableListOf()
  }

  private val filesToHandle: MutableSet<VirtualFile> =
    Collections.synchronizedSet(VirtualFileSetFactory.getInstance().createCompactVirtualFileSet())

  override fun beforeDocumentChange(event: DocumentEvent) {
    val file = LspDidChangeUtil.getFileToHandle(event) ?: return
    LspServerManagerImpl.forEachRunningServerInEachProject { server ->
      server.documentSyncManager.onBeforeDocumentChange(event, file)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    val file = LspDidChangeUtil.getFileToHandle(event) ?: return
    LspServerManagerImpl.forEachRunningServerInEachProject { server ->
      server.documentSyncManager.onDocumentChanged(event, file)
    }

    FileDocumentManager.getInstance().getFile(event.document)?.let { filesToHandle.add(it) }
    scheduleEventsProcessing()
  }

  private fun scheduleEventsProcessing() {
    ReadAction
      .nonBlocking<ChangedFilesData> {
        val data = ChangedFilesData()

        synchronized(filesToHandle) {
          data.handledFiles.addAll(filesToHandle)
        }

        val fileDocumentManager = FileDocumentManager.getInstance()
        LspServerManagerImpl.forEachRunningServerInEachProject { server ->
          for (file in data.handledFiles) {
            ProgressManager.checkCanceled()
            if (!fileDocumentManager.isFileModified(file)) {
              continue  // didOpen not needed
            }

            if (!file.isInLocalFileSystem) continue

            if (!server.isFileOpened(file) && server.isSupportedFile(file)) {
              data.serversToSendDidOpen.add(server to file)
            }
          }
        }

        data
      }
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.nonModal()) { data: ChangedFilesData ->
        filesToHandle.removeAll(data.handledFiles)
        if (data.serversToSendDidOpen.isEmpty()) return@finishOnUiThread

        WriteAction.run<RuntimeException> {
          data.serversToSendDidOpen.forEach { serverAndFile: Pair<LspServerImpl, VirtualFile> ->
            serverAndFile.first.documentSyncManager.open(serverAndFile.second)
          }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}


@ApiStatus.Internal
object LspDidChangeUtil {
  fun getFileToHandle(event: DocumentEvent): VirtualFile? =
    FileDocumentManager.getInstance().getFile(event.document)?.takeIf {
      it.isInLocalFileSystem && !StringUtil.equals(event.oldFragment, event.newFragment)
    }

  /**
   * At the moment of this function call the [documentEvent] must be not yet applied to the document,
   * which effectively means that this function must be called from [DocumentListener.beforeDocumentChange].
   * This is needed to calculate line numbers in the document as they were *before* the change.
   */
  @RequiresEdt
  fun createIncrementalDidChangeParamsBeforeDocumentChange(
    lspServer: LspServer,
    documentEvent: DocumentEvent,
    virtualFile: VirtualFile,
  ): DidChangeTextDocumentParams {
    val versionedIdentifier = getVersionedIdentifier(lspServer, documentEvent.document, virtualFile)
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
    lspServer: LspServer,
    document: Document,
    virtualFile: VirtualFile,
  ): DidChangeTextDocumentParams =
    DidChangeTextDocumentParams(getVersionedIdentifier(lspServer, document, virtualFile),
                                listOf(TextDocumentContentChangeEvent(document.text)))

  private fun getVersionedIdentifier(
    lspServer: LspServer,
    document: Document,
    virtualFile: VirtualFile,
  ): VersionedTextDocumentIdentifier =
    VersionedTextDocumentIdentifier(lspServer.descriptor.getFileUri(virtualFile),
                                    lspServer.getDocumentVersion(document))
}
