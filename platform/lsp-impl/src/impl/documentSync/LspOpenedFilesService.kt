package com.intellij.platform.lsp.impl.documentSync

import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.MultiMap
import java.util.Collections

/**
 * @see processOpenedFiles
 * @see scheduleClosingFilesThatAreNotOfInterest
 */
@Service(Service.Level.PROJECT)
internal class LspOpenedFilesService(private val project: Project) {
  companion object {
    fun getInstance(project: Project): LspOpenedFilesService = project.service()
  }

  // a linked map keeps the report order, so a batch processes the files in a predictable order;
  // the value is the start request stamp of the latest report, see LspClientManagerImpl.startRequestStamp
  private val openedFilesToHandle: MutableMap<VirtualFile, Long> = Collections.synchronizedMap(LinkedHashMap())
  private val openFilesCoalesceObject = Any()
  private val closeFilesCoalesceObject = Any()

  /**
   * Makes sure that the passed [files] are handled by the LSP servers that want to handle them. This means that:
   * - for already running LSP servers that want to handle one or more files from the passed [files],
   * this function makes sure that the corresponding `textDocument/didOpen` requests have been sent to the server
   * - if some servers want to handle one or more files from the passed [files], but they haven't been started yet, they get started
   */
  fun processOpenedFiles(files: Collection<VirtualFile>) {
    if (!TrustedProjects.isProjectTrusted(project)) return
    if (!LspIntegrationProvider.hasAnyExtensions()) return

    // captured before any scheduling: a stop that comes after this report must win over the starts it requests
    val requestStamp = LspClientManagerImpl.getInstanceImpl(project).startRequestStamp()
    // a changed stamp needs a new batch too: the in-flight batch may carry the old stamp, and its starts are stale
    var changed = false
    // LSP servers are external processes: never send the content of files opened in the safe mode to them
    files.asSequence()
      .filter { it.isInLocalFileSystem && TrustedFiles.isTrusted(it, project) }
      .forEach { if (openedFilesToHandle.put(it, requestStamp) != requestStamp) changed = true }
    if (changed) scheduleOpenedFilesProcessing()
  }

  private fun scheduleOpenedFilesProcessing() {
    class ClientToStart(
      val providerClass: Class<out LspIntegrationProvider>,
      val descriptor: LspClientDescriptor,
      val requestStamp: Long,
    )

    class OpenedFilesData {
      val handledFiles: MutableMap<VirtualFile, Long> = LinkedHashMap()
      val clientsToSendDidOpen: MultiMap<LspClientImpl, VirtualFile> = MultiMap()
      val newClientsToStart: MutableCollection<ClientToStart> = mutableListOf()
    }

    val manager = LspClientManagerImpl.getInstanceImpl(project)

    ReadAction.nonBlocking<OpenedFilesData> {
      val data = OpenedFilesData()
      synchronized(openedFilesToHandle) {
        data.handledFiles.putAll(openedFilesToHandle)
      }

      for (provider in LspIntegrationProvider.getAllExtensions()) {
        val providerClass: Class<out LspIntegrationProvider> = provider.javaClass
        val clientsForProvider = manager.getClients(providerClass)

        for ((openedFile, requestStamp) in data.handledFiles) {
          var fileWithinServerRootsAndSupported = false
          for (lspClient in clientsForProvider) {
            ProgressManager.checkCanceled()
            if (lspClient.descriptor.roots.any { VfsUtilCore.isAncestor(it, openedFile, true) } && lspClient.isSupportedFile(openedFile)) {
              fileWithinServerRootsAndSupported = true
            }

            if (lspClient.state == LspServerState.Running &&
                !lspClient.isFileOpened(openedFile) &&
                lspClient.isSupportedFile(openedFile)) {
              data.clientsToSendDidOpen.putValue(lspClient, openedFile)
            }
          }

          if (!fileWithinServerRootsAndSupported && ProjectFileIndex.getInstance(project).isInContent(openedFile)) {
            val starter = LspClientManagerImpl.LspStarterImpl()
            provider.fileOpened(project, openedFile, starter)
            starter.descriptor?.let { descriptor -> data.newClientsToStart.add(ClientToStart(providerClass, descriptor, requestStamp)) }
          }
        }
      }

      data
    }
      .coalesceBy(openFilesCoalesceObject)
      .expireWith(manager)
      .finishOnUiThread(ModalityState.nonModal()) { data: OpenedFilesData ->
        // a value-matched remove keeps an entry that a newer report re-stamped while the batch was computed
        data.handledFiles.forEach { (file, requestStamp) -> openedFilesToHandle.remove(file, requestStamp) }
        if (!data.clientsToSendDidOpen.isEmpty) {
          WriteAction.run<RuntimeException> {
            for ((client, filesToOpen) in data.clientsToSendDidOpen.entrySet()) {
              for (fileToOpen in filesToOpen) {
                client.documentSyncManager.open(fileToOpen)
              }
            }
          }
        }
        data.newClientsToStart.forEach {
          manager.ensureStarted(it.providerClass, it.descriptor, it.requestStamp)
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  /**
   * For every running LSP server, sends `didClose` for files that are no longer open in the editor and are saved.
   * The work is coalesced across calls, so it's cheap to invoke after any event that might have made some files irrelevant.
   */
  fun scheduleClosingFilesThatAreNotOfInterest() {
    val lspClientManager = LspClientManagerImpl.getInstanceImpl(project)
    val runningClients = lspClientManager.getRunningClients()
    if (runningClients.isEmpty()) return

    ReadAction
      .nonBlocking<MultiMap<LspClientImpl, VirtualFile>> {
        val clientToFilesToClose = MultiMap<LspClientImpl, VirtualFile>()
        for (client in runningClients) {
          val filesToClose = client.documentSyncManager.getFilesToClose()
          if (!filesToClose.isEmpty()) {
            clientToFilesToClose.put(client, filesToClose)
          }
        }
        clientToFilesToClose
      }
      .expireWith(lspClientManager)
      .coalesceBy(closeFilesCoalesceObject)
      .finishOnUiThread(ModalityState.nonModal()) { clientToFilesToClose: MultiMap<LspClientImpl, VirtualFile> ->
        if (!clientToFilesToClose.isEmpty) {
          WriteAction.run<RuntimeException> {
            clientToFilesToClose.entrySet().forEach { (client, files) ->
              files.forEach { client.documentSyncManager.close(it) }
            }
          }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}
