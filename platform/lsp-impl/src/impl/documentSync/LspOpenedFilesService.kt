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
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
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
      val serversToSendDidOpen: MultiMap<LspServerImpl, VirtualFile> = MultiMap()
      val newServersToStart: MutableCollection<Pair<Class<out LspClientProvider>, LspClientDescriptor>> = mutableListOf()
    }

    val lspServerManager = LspServerManagerImpl.getInstanceImpl(project)

    ReadAction.nonBlocking<OpenedFilesData> {
      val data = OpenedFilesData()
      synchronized(openedFilesToHandle) {
        data.handledFiles.addAll(openedFilesToHandle)
      }

      for (provider in LspClientProvider.getAllExtensions()) {
        val providerClass: Class<out LspClientProvider> = provider.javaClass
        val serversForProvider = lspServerManager.getClientsForProvider(providerClass)
        var fileWithinServerRootsAndSupported = false

        for (openedFile in data.handledFiles) {
          for (lspServer in serversForProvider) {
            ProgressManager.checkCanceled()
            if (lspServer.descriptor.roots.any { VfsUtilCore.isAncestor(it, openedFile, true) } && lspServer.isSupportedFile(openedFile)) {
              fileWithinServerRootsAndSupported = true
            }

            if (lspServer.state == LspServerState.Running &&
                !lspServer.isFileOpened(openedFile) &&
                lspServer.isSupportedFile(openedFile)) {
              data.serversToSendDidOpen.putValue(lspServer, openedFile)
            }
          }

          if (!fileWithinServerRootsAndSupported && ProjectFileIndex.getInstance(project).isInContent(openedFile)) {
            val starter = LspServerManagerImpl.LspStarterImpl()
            provider.fileOpened(project, openedFile, starter)
            starter.descriptor?.let { descriptor -> data.newServersToStart.add(providerClass to descriptor) }
          }
        }
      }

      data
    }
      .coalesceBy(openFilesCoalesceObject)
      .expireWith(lspServerManager)
      .finishOnUiThread(ModalityState.nonModal()) { data: OpenedFilesData ->
        openedFilesToHandle.removeAll(data.handledFiles)
        if (!data.serversToSendDidOpen.isEmpty) {
          WriteAction.run<RuntimeException> {
            for ((server, filesToOpen) in data.serversToSendDidOpen.entrySet()) {
              for (fileToOpen in filesToOpen) {
                server.documentSyncManager.open(fileToOpen)
              }
            }
          }
        }
        data.newServersToStart.forEach { (providerClass, descriptor) ->
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
    val lspServerManager = LspServerManagerImpl.getInstanceImpl(project)
    val runningServers = lspServerManager.getAllRunningServers()
    if (runningServers.isEmpty()) return

    ReadAction
      .nonBlocking<MultiMap<LspServerImpl, VirtualFile>> {
        val serverToFilesToClose = MultiMap<LspServerImpl, VirtualFile>()
        for (server in runningServers) {
          val filesToClose = server.documentSyncManager.getFilesToClose()
          if (!filesToClose.isEmpty()) {
            serverToFilesToClose.put(server, filesToClose)
          }
        }
        serverToFilesToClose
      }
      .expireWith(lspServerManager)
      .coalesceBy(closeFilesCoalesceObject)
      .finishOnUiThread(ModalityState.nonModal()) { serverToFilesToClose: MultiMap<LspServerImpl, VirtualFile> ->
        if (!serverToFilesToClose.isEmpty) {
          WriteAction.run<RuntimeException> {
            serverToFilesToClose.entrySet().forEach { (server, files) ->
              files.forEach { server.documentSyncManager.close(it) }
            }
          }
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}
