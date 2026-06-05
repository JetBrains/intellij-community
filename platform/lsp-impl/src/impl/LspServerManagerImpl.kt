package com.intellij.platform.lsp.impl

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.Lsp4jServerWrapper
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManager.Companion.getInstance
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState.Initializing
import com.intellij.platform.lsp.api.LspServerState.Running
import com.intellij.platform.lsp.api.LspServerState.ShutdownNormally
import com.intellij.platform.lsp.api.LspServerState.ShutdownUnexpectedly
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.LspServerStarter
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.platform.lsp.impl.features.codeLens.LSP_CODE_VISION_PROVIDER_ID
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.addIfNotNull
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val logger = logger<LspServerManagerImpl>()
private const val MAX_LSP_SERVERS = 10

/**
 * Project service for managing LSP servers for the current project
 */
@ApiStatus.Internal
class LspServerManagerImpl internal constructor(private val project: Project, internal val cs: CoroutineScope) :
  LspServerManager, Disposable {
  init {
    assert(!project.isDefault) { "LspServerManager doesn't make sense for the default project" }
    addExtensionPointListener()
    addWorkspaceModelListener()
  }

  private val lspServers: MutableCollection<LspServerImpl> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val lsp4jServerWrappers = ContainerUtil.createLockFreeCopyOnWriteList<Lsp4jServerWrapper>()

  private val eventDispatcher = EventDispatcher.create(LspServerManagerListener::class.java)

  override fun getServersForProvider(providerClass: Class<out LspServerSupportProvider>): Collection<LspServerImpl> =
    lspServers.filter { it.providerClass == providerClass }

  internal fun getServersWithThisFileOpen(file: VirtualFile): Collection<LspServerImpl> =
    lspServers.filter { it.isFileOpened(file) }

  internal fun getAllRunningServers(): Collection<LspServerImpl> = lspServers.filter { it.state == Running }

  internal fun findRunningServer(condition: (LspServerImpl) -> Boolean): LspServerImpl? =
    lspServers.find { it.state == Running && condition(it) }

  override fun startServersIfNeeded(providerClass: Class<out LspServerSupportProvider>) {
    if (!TrustedProjects.isProjectTrusted(project)) return

    val provider = LspServerSupportProvider.EP_NAME.findExtension(providerClass)
    if (provider == null) {
      logger.error(providerClass.name + " is not loaded")
      return
    }

    cs.launch {
      val descriptorsToStart = readAction {
        val runningServers = getServersForProvider(providerClass)
        val descriptorsToStart = mutableListOf<LspServerDescriptor>()

        for (file in FileEditorManager.getInstance(project).openFiles) {
          ProgressManager.checkCanceled()
          if (!file.isInLocalFileSystem) continue
          if (!ProjectFileIndex.getInstance(project).isInContent(file)) continue

          if (runningServers.any { server ->
              server.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, file, true) }
            }) {
            // the file is already within the roots of a running server
            continue
          }

          if (descriptorsToStart.any { descriptor ->
              descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, file, true) }
            }) {
            // the file is already within the roots of a server that will start soon
            continue
          }

          val starter = LspServerStarterImpl()
          provider.fileOpened(project, file, starter)
          descriptorsToStart.addIfNotNull(starter.descriptor)
        }
        descriptorsToStart
      }

      descriptorsToStart.forEach { ensureServerStarted(providerClass, it) }
    }
  }

  private fun LspServerDescriptor.getServerId(): String = "${javaClass.name}:${presentableName}:${roots.joinToString(":") { it.path }}"

  override fun ensureServerStarted(providerClass: Class<out LspServerSupportProvider>, descriptor: LspServerDescriptor) {
    if (!TrustedProjects.isProjectTrusted(project)) return

    cs.launch {
      readAndEdtWriteAction {
        if (lspServers.any { server -> server.providerClass == providerClass && server.descriptor.getServerId() == descriptor.getServerId() }) {
          return@readAndEdtWriteAction value(Unit)
        }

        if (lspServers.size >= MAX_LSP_SERVERS) {
          logger.error("${lspServers.size} LSP servers are already running and one more wants to start." +
                       "To save system resources, this request will be ignored: $descriptor")
          return@readAndEdtWriteAction value(Unit)
        }

        writeAction {
          val server = LspServerImpl(providerClass, descriptor, eventDispatcher.multicaster, ::wrapLsp4jServer)
          server.start()
          lspServers.add(server)
          Unit
        }
      }
    }
  }

  override fun stopServers(providerClass: Class<out LspServerSupportProvider>): Unit =
    getServersForProvider(providerClass).forEach { stopRunningServer(it) }

  /**
   * Called when the server works fine but needs to be stopped for some reason.
   * For example, an action like `Stop server` or `Restart server` is invoked, or the project is closed.
   */
  internal fun stopRunningServer(lspServer: LspServerImpl) =
    lspServer.ensureServerStopped(explicitStop = true) {
      handleServerStop(lspServer, explicitStop = true)
    }

  /**
   * Called when the IDE detects that the server stopped working, for example,
   * the server process has terminated or the socket connection has been lost.
   * 1. This might be expected because the server has recently been stopped by calling [stopRunningServer].
   * In this case, this function doesn't do anything.
   * 2. Otherwise, the server termination is treated as an unexpected one.
   */
  internal fun handleMaybeUnexpectedServerStop(lspServer: LspServerImpl, serverOutput: String) =
    lspServer.ensureServerStopped(explicitStop = false) {
      if (lspServer.state != ShutdownNormally) lspServer.appendServerErrorOutput(serverOutput)
      handleServerStop(lspServer, explicitStop = false)
    }

  /**
   * Called from [stopRunningServer] and from [handleMaybeUnexpectedServerStop]. Not expected to be called from anywhere else.
   * @param explicitStop
   * - `true` is passed by [stopRunningServer], which is called when the server is working fine but needs to be stopped for some reason
   * - `false` is passed by [handleMaybeUnexpectedServerStop].
   * It is called when the IDE detects that the server stopped working, which may be both expected and unexpected
   */
  private fun handleServerStop(lspServer: LspServerImpl, explicitStop: Boolean) {
    if (lspServer.state in arrayOf(Initializing, Running) && !lspServers.contains(lspServer)) {
      logger.error("LspServerManager doesn't know the server that it is asked to stop: $lspServer")
    }

    if (explicitStop) {
      // The server might be already ShutdownUnexpectedly at this point.
      // `explicitStop == true` for a server that is already ShutdownUnexpectedly means one of the following:
      // - project closed,
      // - plugin unloaded,
      // - plugin-specific technology disabled in Settings (stopServers(providerClass) called),
      // - manual server restart (RestartLspServerAction).
      // In any case, we need to remove it from the lspServers list
      lspServers.remove(lspServer)
    }
    else {
      // ShutdownUnexpectedly servers stay in the `lspServers` list so that they show up as 'Terminated' in the status bar widget.
      // By the way, maybe try to auto-restart the server a couple of times if it has shutdown unexpectedly?
    }

    if (lspServer.state == Running) {
      DaemonCodeAnalyzer.getInstance(project).restart("LspServerManagerImpl.stop")
    }
  }

  override fun stopAndRestartIfNeeded(providerClass: Class<out LspServerSupportProvider>) {
    stopServers(providerClass)
    startServersIfNeeded(providerClass)
  }

  override fun addLsp4jServerWrapper(wrapper: Lsp4jServerWrapper, parentDisposable: Disposable) {
    lsp4jServerWrappers.add(wrapper)
    Disposer.register(parentDisposable) {
      lsp4jServerWrappers.remove(wrapper)
    }
  }

  private fun wrapLsp4jServer(lspServer: LspServer, lsp4jServer: Lsp4jServer): Lsp4jServer =
    lsp4jServerWrappers.fold(lsp4jServer) { wrappedServer, wrapper ->
      wrapper.wrapLsp4jServer(lspServer, wrappedServer)
    }

  @ApiStatus.Internal
  override fun addLspServerManagerListener(
    listener: LspServerManagerListener,
    parentDisposable: Disposable,
    sendEventsForExistingServers: Boolean,
  ) {
    eventDispatcher.addListener(listener, parentDisposable)

    if (sendEventsForExistingServers) {
      // Listeners in LspTestUtilKt need to know about events that happened before a test managed to register a listener
      for (lspServer in lspServers) {
        if (lspServer.state == ShutdownUnexpectedly) eventDispatcher.multicaster.serverStateChanged(lspServer)
        lspServer.forEachOpenedFile { eventDispatcher.multicaster.fileOpened(lspServer, it) }
      }
    }
  }

  private fun addExtensionPointListener() {
    LspServerSupportProvider.EP_NAME.point.addExtensionPointListener(
      cs,
      false,
      object : ExtensionPointListener<LspServerSupportProvider> {
        override fun extensionAdded(extension: LspServerSupportProvider, pluginDescriptor: PluginDescriptor) =
          startServersIfNeeded(extension.javaClass)

        override fun extensionRemoved(extension: LspServerSupportProvider, pluginDescriptor: PluginDescriptor) =
          stopServers(extension.javaClass)
      },
    )
  }

  private fun addWorkspaceModelListener() {
    cs.launch {
      project.serviceAsync<WorkspaceModel>().eventLog.collect { event ->
        if (event.getChanges(ContentRootEntity::class.java).isNotEmpty()) {
          onProjectRootsChanged()
        }
      }
    }
  }

  suspend fun onProjectRootsChanged() {
    // The current implementation handles the following use case:
    // - some file is open in the editor, but it doesn't belong to the project (for example, it is in an excluded folder)
    // - later this file becomes a project file for some reason (for example, its parent folder is unexcluded)
    // In this case, it might be needed to send `didOpen` request for this file (if there's an already running LSP server that wants to handle it),
    // or it might be needed to start an LSP server that wants to handle this file.

    // TODO Some running servers might need to update the roots they serve (`didChangeWorkspaceFolders` notification)
    // TODO Some running servers might need to be stopped if they serve roots that don't belong to the project anymore

    readAction {
      val openedFiles = FileEditorManager.getInstance(project).openFiles
      val unsavedFiles = FileDocumentManager.getInstance().unsavedDocuments.mapNotNull { FileDocumentManager.getInstance().getFile(it) }
      LspOpenedFilesService.getInstance(project).processOpenedFiles(unsavedFiles + openedFiles)
    }
  }

  override fun dispose(): Unit = lspServers.forEach { stopRunningServer(it) }


  internal class LspServerStarterImpl : LspServerStarter {
    var descriptor: LspServerDescriptor? = null

    override fun ensureServerStarted(descriptor: LspServerDescriptor) {
      this.descriptor = descriptor
    }
  }


  companion object {
    fun getInstanceImpl(project: Project): LspServerManagerImpl = getInstance(project) as LspServerManagerImpl

    internal inline fun forEachRunningServerInEachProject(action: (LspServerImpl) -> Unit) =
      ProjectManager.getInstance().openProjects.forEach { project ->
        getInstanceImpl(project).lspServers.forEach { server ->
          if (server.state == Running) action(server)
        }
      }

    internal fun isAnyServerRunning(): Boolean =
      ProjectManager.getInstance().openProjects.any { project ->
        getInstanceImpl(project).lspServers.any { it.state == Running }
      }

    @RequiresBackgroundThread
    @RequiresReadLock
    private fun findPsiFileIfOpen(project: Project, file: VirtualFile): PsiFile? {
      val originFile = BackedVirtualFile.getOriginFileIfBacked(file)
      return if (FileEditorManager.getInstance(project).isFileOpen(originFile)) {
        PsiManager.getInstance(project).findFile(file)
      }
      else {
        null
      }
    }

    internal fun refreshBreadcrumbs() {
      application.messageBus.syncPublisher(BreadcrumbsXmlWrapper.FORCE_RELOAD_BREADCRUMBS).run()
    }

    internal fun refreshInlayHints(project: Project) {
      ApplicationManager.getApplication().invokeLater {
        InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
        DaemonCodeAnalyzer.getInstance(project).restart("LspServerManagerImpl.refreshInlayHints")
      }
    }

    internal suspend fun refreshInlayHints(project: Project, file: VirtualFile, reason: Any) {
      val editors = EditorFactory.getInstance().allEditors.filter { it.virtualFile == file }
      val psiFile = readAction {
        findPsiFileIfOpen(project, file)
      }
      withContext(Dispatchers.EDT) {
        editors.forEach { editor ->
          InlayHintsPassFactoryInternal.clearModificationStamp(editor) // needs EDT
        }
        if (psiFile != null && psiFile.isValid) {
          DaemonCodeAnalyzer.getInstance(project).restart(psiFile, reason) // should be run on EDT atomically with clearModificationStamp
        }
      }
    }

    internal fun refreshCodeLenses(project: Project) {
      runInEdt {
        project.service<CodeVisionHost>()
          .invalidateProvider(
            CodeVisionHost.LensInvalidateSignal(null, listOf(LSP_CODE_VISION_PROVIDER_ID))
          )
      }
    }
  }
}
