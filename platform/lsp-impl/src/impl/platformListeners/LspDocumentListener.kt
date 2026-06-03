package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.documentSync.LspDidChangeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.Collections

internal class LspDocumentListener : DocumentListener {
  private class ChangedFilesData {
    val handledFiles: MutableSet<VirtualFile> = VirtualFileSetFactory.getInstance().createCompactVirtualFileSet()
    val clientsToSendDidOpen: MutableCollection<Pair<LspClientImpl, VirtualFile>> = mutableListOf()
  }

  private val filesToHandle: MutableSet<VirtualFile> =
    Collections.synchronizedSet(VirtualFileSetFactory.getInstance().createCompactVirtualFileSet())

  override fun beforeDocumentChange(event: DocumentEvent) {
    val file = LspDidChangeUtil.getFileToHandle(event) ?: return
    LspClientManagerImpl.forEachRunningClientInEachProject { client ->
      client.documentSyncManager.onBeforeDocumentChange(event, file)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    val file = LspDidChangeUtil.getFileToHandle(event) ?: return
    LspClientManagerImpl.forEachRunningClientInEachProject { client ->
      client.documentSyncManager.onDocumentChanged(event, file)
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
        LspClientManagerImpl.forEachRunningClientInEachProject { client ->
          for (file in data.handledFiles) {
            ProgressManager.checkCanceled()
            if (!fileDocumentManager.isFileModified(file)) {
              continue  // didOpen not needed
            }

            if (!file.isInLocalFileSystem) continue

            if (!client.isFileOpened(file) && client.isSupportedFile(file)) {
              data.clientsToSendDidOpen.add(client to file)
            }
          }
        }

        data
      }
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.nonModal()) { data: ChangedFilesData ->
        filesToHandle.removeAll(data.handledFiles)
        if (data.clientsToSendDidOpen.isEmpty()) return@finishOnUiThread

        WriteAction.run<RuntimeException> {
          data.clientsToSendDidOpen.forEach { serverAndFile: Pair<LspClientImpl, VirtualFile> ->
            serverAndFile.first.documentSyncManager.open(serverAndFile.second)
          }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}

