package com.intellij.platform.lsp.impl.documentSync

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
import com.intellij.platform.lsp.api.LspClientProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
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

  private val openedFilesToHandle: MutableSet<VirtualFile> = Collections.synchronizedSet(HashSet())
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
    @Suppress("DEPRECATION")
    if (!LspClientProvider.EP_NAME.hasAnyExtensions() && !LspServerSupportProvider.EP_NAME.hasAnyExtensions()) return

    val added = files.filter { it.isInLocalFileSystem }.let { openedFilesToHandle.addAll(it) }
    if (added) scheduleOpenedFilesProcessing()
  }

  private fun scheduleOpenedFilesProcessing() {
    class OpenedFilesData {
      val handledFiles: MutableSet<VirtualFile> = HashSet()
      val clientsToSendDidOpen: MultiMap<LspClientImpl, VirtualFile> = MultiMap()
      val newClientsToStart: MutableCollection<Pair<Class<out LspClientProvider>, LspClientDescriptor>> = mutableListOf()
    }

    val lspServerManager = LspClientManagerImpl.getInstanceImpl(project)

    ReadAction.nonBlocking<OpenedFilesData> {
      val data = OpenedFilesData()
      synchronized(openedFilesToHandle) {
        data.handledFiles.addAll(openedFilesToHandle)
      }

      for (provider in LspClientProvider.getAllExtensions()) {
        val providerClass: Class<out LspClientProvider> = provider.javaClass
        val clientsForProvider = lspServerManager.getClients(providerClass)
        var fileWithinServerRootsAndSupported = false

        for (openedFile in data.handledFiles) {
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
            starter.descriptor?.let { descriptor -> data.newClientsToStart.add(providerClass to descriptor) }
          }
        }
      }

      data
    }
      .coalesceBy(openFilesCoalesceObject)
      .expireWith(lspServerManager)
      .finishOnUiThread(ModalityState.nonModal()) { data: OpenedFilesData ->
        openedFilesToHandle.removeAll(data.handledFiles)
        if (!data.clientsToSendDidOpen.isEmpty) {
          WriteAction.run<RuntimeException> {
            for ((client, filesToOpen) in data.clientsToSendDidOpen.entrySet()) {
              for (fileToOpen in filesToOpen) {
                client.documentSyncManager.open(fileToOpen)
              }
            }
          }
        }
        data.newClientsToStart.forEach { (providerClass, descriptor) ->
          lspServerManager.ensureClientStarted(providerClass, descriptor)
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  /**
   * For every running LSP server, sends `didClose` for files that are no longer open in the editor and are saved.
   * The work is coalesced across calls, so it's cheap to invoke after any event that might have made some files irrelevant.
   */
  fun scheduleClosingFilesThatAreNotOfInterest() {
    val lspServerManager = LspClientManagerImpl.getInstanceImpl(project)
    val runningClients = lspServerManager.getAllRunningClients()
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
      .expireWith(lspServerManager)
      .coalesceBy(closeFilesCoalesceObject)
      .finishOnUiThread(ModalityState.nonModal()) { serverToFilesToClose: MultiMap<LspClientImpl, VirtualFile> ->
        if (!serverToFilesToClose.isEmpty) {
          WriteAction.run<RuntimeException> {
            serverToFilesToClose.entrySet().forEach { (client, files) ->
              files.forEach { client.documentSyncManager.close(it) }
            }
          }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}
