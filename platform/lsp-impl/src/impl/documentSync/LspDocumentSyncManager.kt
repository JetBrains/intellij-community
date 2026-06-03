package com.intellij.platform.lsp.impl.documentSync

import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import java.util.Collections

/**
 * Sends document lifecycle notifications (didOpen, didChange, didSave, didClose) and tracks which files are open on the server.
 *
 * The platform listeners fan out across running servers and dispatch into this class.
 */
internal class LspDocumentSyncManager(private val client: LspClientImpl) {

  private val openedFiles: MutableSet<VirtualFile> = Collections.synchronizedSet(HashSet())

  val openedFileCount: Int get() = openedFiles.size

  fun isFileOpened(file: VirtualFile): Boolean {
    val originFile = BackedVirtualFile.getOriginFileIfBacked(file)
    return openedFiles.contains(originFile)
  }

  fun forEachOpenedFile(action: (VirtualFile) -> Unit) = openedFiles.forEach(action)

  fun clearOpenedFiles() {
    openedFiles.clear()
  }

  @RequiresWriteLock
  fun open(file: VirtualFile) {
    if (client.state != LspServerState.Running) {
      client.logError("Server is not in the Running state. Ignoring open($file)")
      return
    }

    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document == null) {
      client.logInfo("Skipping didOpen request because there's no document for file $file")
      return
    }

    if (openedFiles.add(file)) {
      client.documentMapping.getAdapterForFile(file).sendDidOpen(client, file, document)

      client.notifyFileOpened(file)
      application.messageBus.syncPublisher(BreadcrumbsXmlWrapper.FORCE_RELOAD_BREADCRUMBS).run()
    }
    else {
      client.logError("open() cannot be called for already opened files. Ignoring: $file")
    }
  }

  @RequiresWriteLock
  fun close(file: VirtualFile) {
    if (!openedFiles.remove(file)) {
      client.logError("close() cannot be called for files that haven't been opened. Ignoring: $file")
      return
    }

    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document == null) {
      // Document already gone — can only send a plain textDocument/didClose.
      // For notebook files this is incorrect (server expects notebookDocument/didClose),
      // but without the document we can't reconstruct cell structure.
      client.logWarn("No document for file $file in close(), sending plain textDocument/didClose as fallback")
      val params = DidCloseTextDocumentParams(client.getDocumentIdentifier(file))
      client.requestExecutor.sendNotification { it.textDocumentService.didClose(params) }
      return
    }
    client.documentMapping.getAdapterForFile(file).sendDidClose(client, file, document)
  }

  /**
   * Iterates all files that are either unsaved or opened in the editor, and sends `textDocument/didOpen` to the server if needed.
   */
  fun openForOpenedOrUnsavedFiles() {
    ReadAction
      .nonBlocking<Set<VirtualFile>> {
        val openedAndUnsavedFiles: MutableSet<VirtualFile> = HashSet()
        for (file in FileEditorManager.getInstance(client.project).openFiles) {
          if (!openedFiles.contains(file) && client.isSupportedFile(file)) {
            openedAndUnsavedFiles.add(file)
          }
        }
        for (document in FileDocumentManager.getInstance().unsavedDocuments) {
          val file = FileDocumentManager.getInstance().getFile(document)
          if (file != null && !openedFiles.contains(file) && client.isSupportedFile(file)) {
            openedAndUnsavedFiles.add(file)
          }
        }
        openedAndUnsavedFiles
      }
      .expireWhen { client.state != LspServerState.Running }
      .finishOnUiThread(ModalityState.nonModal()) { files: Set<VirtualFile> ->
        if (files.isEmpty()) return@finishOnUiThread
        WriteAction.run<RuntimeException> {
          client.logDebug("Opening files after server initialization or after move/rename: $files")
          files.forEach { file: VirtualFile -> open(file) }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  /**
   * @return files that have been opened by this server (with `didOpen` request), but now they are neither opened in the editor nor unsaved
   */
  @RequiresBackgroundThread
  @RequiresReadLock
  fun getFilesToClose(): Collection<VirtualFile> {
    val fileEditorManager = FileEditorManager.getInstance(client.project)
    val fileDocumentManager = FileDocumentManager.getInstance()

    return openedFiles.filter { file ->
      val originFile = BackedVirtualFile.getOriginFileIfBacked(file)
      if (fileEditorManager.isFileOpen(originFile)) return@filter false

      val cachedDoc = FileDocumentManager.getInstance().getCachedDocument(originFile)
      val isSaved = cachedDoc == null || !fileDocumentManager.isDocumentUnsaved(cachedDoc)
      isSaved
    }
  }

  /**
   * Schedules a `textDocument/didSave` notification once the in-progress write action that triggered the save completes.
   */
  fun scheduleSave(file: VirtualFile, document: Document) {
    val didSaveOptions = client.getDidSaveOptions(file) ?: return
    val manager = LspClientManagerImpl.getInstanceImpl(client.project)
    manager.cs.launch {
      // Using `readAction` guarantees that the write action in which `beforeDocumentSaving()` was called has finished,
      // so the file has been physically saved, therefore it's now good time to send `textDocument/didSave`
      readAction {
        client.documentMapping.getAdapterForFile(file).sendDidSave(client, file, document, didSaveOptions.includeText == true)
      }
    }
  }

  /**
   * Per-server handler for [com.intellij.openapi.editor.event.DocumentListener.beforeDocumentChange].
   *
   * Incremental `DidChangeTextDocumentParams` must be created *before* the document mutation, because the params
   * encode line numbers as they were before the change.
   */
  @RequiresEdt
  fun onBeforeDocumentChange(event: DocumentEvent, file: VirtualFile) {
    client.fileEdited(file, event)

    if (client.textDocumentSyncKind == TextDocumentSyncKind.Incremental && isFileOpened(file)) {
      client.documentMapping.getAdapterForFile(file).sendDidChangeIncremental(client, file, event)
    }
  }

  /**
   * Per-server handler for [com.intellij.openapi.editor.event.DocumentListener.documentChanged].
   *
   * Full `DidChangeTextDocumentParams` can only be calculated *after* the document mutation, because it needs the new text.
   */
  @RequiresEdt
  fun onDocumentChanged(event: DocumentEvent, file: VirtualFile) {
    if (client.textDocumentSyncKind == TextDocumentSyncKind.Full && isFileOpened(file)) {
      client.documentMapping.getAdapterForFile(file).sendDidChangeFull(client, file, event.document)
    }
  }
}
