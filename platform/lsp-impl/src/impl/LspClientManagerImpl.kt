package com.intellij.platform.lsp.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

private val logger = logger<LspClientManagerImpl>()
private const val MAX_LSP_SERVERS = 10

/**
 * Project service for managing LSP servers for the current project
 */
@ApiStatus.Internal
class LspClientManagerImpl internal constructor(private val project: Project, internal val cs: CoroutineScope) :
  @Suppress("DEPRECATION")
  LspServerManager, Disposable {
  init {
    assert(!project.isDefault) { "LspServerManager doesn't make sense for the default project" }
    addExtensionPointListener()
    addWorkspaceModelListener()
  }

  private val lspClients: MutableCollection<LspClientImpl> = ContainerUtil.createLockFreeCopyOnWriteList()
  @TestOnly
  private val lsp4jServerWrappers = ContainerUtil.createLockFreeCopyOnWriteList<Lsp4jServerWrapper>()

  private val eventDispatcher = EventDispatcher.create(LspClientManagerListener::class.java)

  override fun getClients(providerClass: Class<out LspIntegrationProvider>): Collection<LspClientImpl> =
    lspClients.filter { it.providerClass == providerClass }

  @Deprecated("Use getClients", ReplaceWith("getClients(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun getServersForProvider(providerClass: Class<out LspServerSupportProvider>): Collection<LspClientImpl> =
    lspClients.filter { it.providerClass == providerClass }

  internal fun getClientsWithThisFileOpen(file: VirtualFile): Collection<LspClientImpl> =
    lspClients.filter { it.isFileOpened(file) }

  internal fun getRunningClients(): Collection<LspClientImpl> = lspClients.filter { it.state == LspServerState.Running }

  internal fun findRunningClient(condition: (LspClientImpl) -> Boolean): LspClientImpl? =
    lspClients.find { it.state == LspServerState.Running && condition(it) }

  override fun startClientsIfNeeded(providerClass: Class<out LspIntegrationProvider>): Unit = startIfNeeded(providerClass)

  @Deprecated("Use startClientsIfNeeded", ReplaceWith("startClientsIfNeeded(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "UNCHECKED_CAST")
  override fun startServersIfNeeded(providerClass: Class<out LspServerSupportProvider>): Unit =
    startIfNeeded(providerClass)

  private fun startIfNeeded(providerClass: Class<out LspIntegrationProvider>) {
    if (!TrustedProjects.isProjectTrusted(project)) return

    val provider = LspIntegrationProvider.getAllExtensions().firstOrNull { it.javaClass == providerClass }
    if (provider == null) {
      logger.error(providerClass.name + " is not loaded")
      return
    }

    cs.launch {
      val descriptorsToStart = readAction {
        val clients = getClients(providerClass)
        val descriptorsToStart = mutableListOf<LspClientDescriptor>()

        for (file in FileEditorManager.getInstance(project).openFiles) {
          ProgressManager.checkCanceled()
          if (!file.isInLocalFileSystem) continue
          if (!ProjectFileIndex.getInstance(project).isInContent(file)) continue

          if (clients.any { client ->
              client.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, file, true) }
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

          val starter = LspStarterImpl()
          callFileOpened(provider, file, starter)
          descriptorsToStart.addIfNotNull(starter.descriptor)
        }
        descriptorsToStart
      }

      descriptorsToStart.forEach { ensureStarted(providerClass, it) }
    }
  }

  private fun callFileOpened(provider: LspIntegrationProvider, file: VirtualFile, starter: LspStarterImpl): Unit =
    provider.fileOpened(project, file, starter)

  private fun LspClientDescriptor.getServerId(): String = "${javaClass.name}:${presentableName}:${roots.joinToString(":") { it.path }}"

  override fun ensureClientStarted(providerClass: Class<out LspIntegrationProvider>, descriptor: LspClientDescriptor): Unit =
    ensureStarted(providerClass, descriptor)

  @Deprecated("Use ensureClientStarted", ReplaceWith("ensureClientStarted(providerClass, descriptor)"))
  @Suppress("DEPRECATION", "UNCHECKED_CAST")
  override fun ensureServerStarted(providerClass: Class<out LspServerSupportProvider>, descriptor: LspServerDescriptor): Unit =
    ensureStarted(providerClass as Class<out LspIntegrationProvider>, descriptor)

  private fun ensureStarted(providerClass: Class<out LspIntegrationProvider>, descriptor: LspClientDescriptor) {
    if (!TrustedProjects.isProjectTrusted(project)) return

    cs.launch {
      readAndEdtWriteAction {
        if (lspClients.any { client -> client.providerClass == providerClass && client.descriptor.getServerId() == descriptor.getServerId() }) {
          return@readAndEdtWriteAction value(Unit)
        }

        if (lspClients.size >= MAX_LSP_SERVERS) {
          logger.error("${lspClients.size} LSP servers are already running and one more wants to start." +
                       "To save system resources, this request will be ignored: $descriptor")
          return@readAndEdtWriteAction value(Unit)
        }

        writeAction {
          val client = LspClientImpl(providerClass, descriptor, eventDispatcher.multicaster)
          client.start()
          lspClients.add(client)
          Unit
        }
      }
    }
  }

  override fun stopClients(providerClass: Class<out LspIntegrationProvider>): Unit =
    getClients(providerClass).forEach { stopRunningServer(it) }

  @Deprecated("Use stopClients", ReplaceWith("stopClients(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "UNCHECKED_CAST")
  override fun stopServers(providerClass: Class<out LspServerSupportProvider>): Unit =
    stopClients(providerClass as Class<out LspIntegrationProvider>)

  /**
   * Called when the server works fine but needs to be stopped for some reason.
   * For example, an action like `Stop server` or `Restart server` is invoked, or the project is closed.
   */
  internal fun stopRunningServer(lspClient: LspClientImpl) =
    lspClient.ensureServerStopped(explicitStop = true) {
      handleServerStop(lspClient, explicitStop = true)
    }

  /**
   * Called when the IDE detects that the server stopped working, for example,
   * the server process has terminated or the socket connection has been lost.
   * 1. This might be expected because the server has recently been stopped by calling [stopRunningServer].
   * In this case, this function doesn't do anything.
   * 2. Otherwise, the server termination is treated as an unexpected one.
   */
  internal fun handleMaybeUnexpectedServerStop(lspClient: LspClientImpl, serverOutput: String) =
    lspClient.ensureServerStopped(explicitStop = false) {
      if (lspClient.state != LspServerState.ShutdownNormally) lspClient.appendServerErrorOutput(serverOutput)
      handleServerStop(lspClient, explicitStop = false)
    }

  /**
   * Called from [stopRunningServer] and from [handleMaybeUnexpectedServerStop]. Not expected to be called from anywhere else.
   * @param explicitStop
   * - `true` is passed by [stopRunningServer], which is called when the server is working fine but needs to be stopped for some reason
   * - `false` is passed by [handleMaybeUnexpectedServerStop].
   * It is called when the IDE detects that the server stopped working, which may be both expected and unexpected
   */
  private fun handleServerStop(lspClient: LspClientImpl, explicitStop: Boolean) {
    if (lspClient.state in arrayOf(LspServerState.Initializing, LspServerState.Running) && !lspClients.contains(lspClient)) {
      logger.error("LspServerManager doesn't know the server that it is asked to stop: $lspClient")
    }

    if (explicitStop) {
      // The serverState might be already ShutdownUnexpectedly at this point.
      // `explicitStop == true` for a server that is already ShutdownUnexpectedly means one of the following:
      // - project closed,
      // - plugin unloaded,
      // - plugin-specific technology disabled in Settings (stopServers(providerClass) called),
      // - manual server restart (RestartLspServerAction).
      // In any case, we need to remove it from the `lspClients` collection
      lspClients.remove(lspClient)
    }
    else {
      // ShutdownUnexpectedly servers stay in the `lspClients` collection so that they show up as 'Terminated' in the status bar widget.
      // By the way, maybe try to auto-restart the server a couple of times if it has shutdown unexpectedly?
    }

    if (lspClient.state == LspServerState.Running) {
      DaemonCodeAnalyzer.getInstance(project).restart("LspClientManagerImpl.stop")
    }
  }

  override fun stopAndRestartClientsIfNeeded(providerClass: Class<out LspIntegrationProvider>) {
    stopClients(providerClass)
    startClientsIfNeeded(providerClass)
  }

  @Deprecated("Use stopAndRestartClientsIfNeeded", ReplaceWith("stopAndRestartClientsIfNeeded(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "UNCHECKED_CAST")
  override fun stopAndRestartIfNeeded(providerClass: Class<out LspServerSupportProvider>): Unit =
    stopAndRestartClientsIfNeeded(providerClass as Class<out LspIntegrationProvider>)

  @TestOnly
  override fun addLsp4jServerWrapper(wrapper: Lsp4jServerWrapper, parentDisposable: Disposable) {
    lsp4jServerWrappers.add(wrapper)
    Disposer.register(parentDisposable) {
      lsp4jServerWrappers.remove(wrapper)
    }
  }

  internal fun wrapLsp4jServer(lspServer: LspServer, lsp4jServer: Lsp4jServer): Lsp4jServer =
    @Suppress("TestOnlyProblems")
    lsp4jServerWrappers.fold(lsp4jServer) { wrappedLsp4jServer, wrapper ->
      wrapper.wrapLsp4jServer(lspServer, wrappedLsp4jServer)
    }

  override fun addListener(listener: LspClientManagerListener, parentDisposable: Disposable, sendEventsForExistingClients: Boolean) {
    eventDispatcher.addListener(listener, parentDisposable)

    if (sendEventsForExistingClients) {
      // Listeners in LspTestUtilKt need to know about events that happened before a test managed to register a listener
      for (lspClient in lspClients) {
        if (lspClient.state == LspServerState.ShutdownUnexpectedly) eventDispatcher.multicaster.serverStateChanged(lspClient)
        lspClient.forEachOpenedFile { eventDispatcher.multicaster.fileOpened(lspClient, it) }
      }
    }
  }

  private fun addExtensionPointListener() {
    LspIntegrationProvider.EP_NAME.point.addExtensionPointListener(
      cs,
      false,
      object : ExtensionPointListener<LspIntegrationProvider> {
        override fun extensionAdded(extension: LspIntegrationProvider, pluginDescriptor: PluginDescriptor): Unit =
          startClientsIfNeeded(extension.javaClass)

        override fun extensionRemoved(extension: LspIntegrationProvider, pluginDescriptor: PluginDescriptor): Unit =
          stopClients(extension.javaClass)
      },
    )
    @Suppress("DEPRECATION")
    LspServerSupportProvider.EP_NAME.point.addExtensionPointListener(
      cs,
      false,
      object : ExtensionPointListener<LspServerSupportProvider> {
        override fun extensionAdded(extension: LspServerSupportProvider, pluginDescriptor: PluginDescriptor): Unit =
          startIfNeeded(extension.javaClass)

        override fun extensionRemoved(extension: LspServerSupportProvider, pluginDescriptor: PluginDescriptor): Unit =
          stopClients(extension.javaClass)
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

  override fun dispose(): Unit = lspClients.forEach { stopRunningServer(it) }


  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  internal class LspStarterImpl : LspIntegrationProvider.LspClientStarter, LspServerSupportProvider.LspServerStarter {
    var descriptor: LspClientDescriptor? = null

    override fun ensureClientStarted(descriptor: LspClientDescriptor) {
      this.descriptor = descriptor
    }

    override fun ensureServerStarted(descriptor: LspServerDescriptor) {
      this.descriptor = descriptor
    }
  }


  companion object {
    fun getInstanceImpl(project: Project): LspClientManagerImpl = LspClientManager.getInstance(project) as LspClientManagerImpl

    internal inline fun forEachRunningClientInEachProject(action: (LspClientImpl) -> Unit) =
      ProjectManager.getInstance().openProjects.forEach { project ->
        getInstanceImpl(project).lspClients.forEach { client ->
          if (client.state == LspServerState.Running) action(client)
        }
      }

    internal fun isAnyServerRunning(): Boolean =
      ProjectManager.getInstance().openProjects.any { project ->
        getInstanceImpl(project).lspClients.any { it.state == LspServerState.Running }
      }
  }
}
