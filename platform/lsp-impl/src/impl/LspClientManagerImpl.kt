package com.intellij.platform.lsp.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
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
import com.intellij.platform.lsp.api.LspBulkIntegrationProvider
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.platform.lsp.impl.features.navigation.LspDynamicFiles
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewSupport
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.runSuppressing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

private val logger = logger<LspClientManagerImpl>()
private const val MAX_LSP_CLIENTS = 10

/**
 * The stable identity of a server: the same across sessions, so a restarted client can recognize its predecessor's files.
 * The provider class is part of the identity, because two providers can use the same descriptor class, name, and roots.
 */
internal fun getServerId(providerClass: Class<out LspIntegrationProvider>, descriptor: LspClientDescriptor): String =
  "${providerClass.name}:${descriptor.javaClass.name}:${descriptor.presentableName}:${descriptor.roots.joinToString(":") { it.path }}"

internal fun LspClientImpl.getServerId(): String = getServerId(providerClass, descriptor)

/**
 * Project service for managing LSP servers for the current project
 */
@ApiStatus.Internal
class LspClientManagerImpl internal constructor(private val project: Project, internal val cs: CoroutineScope) :
  @Suppress("DEPRECATION")
  LspServerManager, Disposable {
  init {
    assert(!project.isDefault) { "LspClientManager doesn't make sense for the default project" }
    addExtensionPointListener()
    addWorkspaceModelListener()
  }

  private val lspClients: MutableList<LspClientImpl> = ContainerUtil.createLockFreeCopyOnWriteList()

  /**
   * Makes a client start in [ensureStarted] atomic with the stop-stamp writes and the client snapshot in [stopClients].
   * It also guards [LspClientImpl.stopRequested], [autoRestartLimit], and the decisions on an automatic restart.
   * Do not take the state lock of a client while you hold [startStopLock].
   * [handleServerStop] runs under that state lock, and the listeners of its events can call [stopClients].
   */
  private val startStopLock = Any()
  private val stopClock = AtomicLong()

  /**
   * Keyed by the provider class name for [stopClients], and by the server id for [stopRunningServer].
   * A string key is required: a `Class` key would retain the classloader of an unloaded plugin.
   */
  private val lastStopStamps = ConcurrentHashMap<String, Long>()

  private val autoRestartLimit = LspAutoRestartLimit.fromRegistry()

  /** The pending restarts of [scheduleAutoRestart], keyed by the server id. */
  private val pendingAutoRestarts = ConcurrentHashMap<String, Job>()

  @TestOnly
  private val lsp4jServerWrappers = ContainerUtil.createLockFreeCopyOnWriteList<Lsp4jServerWrapper>()

  private val eventDispatcher = EventDispatcher.create(LspClientManagerListener::class.java)

  override fun getClients(providerClass: Class<out LspIntegrationProvider>): Collection<LspClientImpl> =
    lspClients.filter { it.providerClass == providerClass }

  internal fun getAllClients(): List<LspClientImpl> = lspClients.toList()

  @Deprecated("Use getClients", ReplaceWith("getClients(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun getServersForProvider(providerClass: Class<out LspServerSupportProvider>): Collection<LspClientImpl> =
    lspClients.filter { it.providerClass == providerClass }

  internal fun getClientsWithThisFileOpen(file: VirtualFile): Collection<LspClientImpl> =
    lspClients.filter { it.isFileOpened(file) }

  /**
   * Clients that should handle requests like hover or go-to-definition for the given file.
   * A dynamic file goes to the client that produced it (see [LspDynamicFiles]): an in-memory content file is requested
   * with the URI it was produced from.
   * A content file that lost its producing client, for example after a server restart, is adopted:
   * the replacement of the producing client, found by the same server identity, registers the file
   * and reports it open again, so existing editor tabs keep working.
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  internal fun getClientsForFileRequests(file: VirtualFile): Collection<LspClientImpl> {
    val clientsWithFileOpen = getClientsWithThisFileOpen(file)
    if (clientsWithFileOpen.isNotEmpty()) return clientsWithFileOpen
    val producingClients = getRunningClients().filter { it.dynamicFiles.contains(file) }
    if (producingClients.isNotEmpty()) return producingClients
    val contentUri = LspDynamicFiles.getDynamicFileUri(file) ?: return emptyList()
    return getRunningClients().filter { it.dynamicFiles.adopt(file, contentUri) }
  }

  internal fun getRunningClients(): Collection<LspClientImpl> = lspClients.filter { it.state == LspServerState.Running }

  internal fun hasInitializingOrRunningClients(): Boolean =
    lspClients.any { it.state == LspServerState.Initializing || it.state == LspServerState.Running }

  internal fun findRunningClient(condition: (LspClientImpl) -> Boolean): LspClientImpl? =
    lspClients.find { it.state == LspServerState.Running && condition(it) }

  override fun startClientsIfNeeded(providerClass: Class<out LspIntegrationProvider>): Unit = startIfNeeded(providerClass)

  @Deprecated("Use startClientsIfNeeded", ReplaceWith("startClientsIfNeeded(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "UNCHECKED_CAST")
  override fun startServersIfNeeded(providerClass: Class<out LspServerSupportProvider>): Unit =
    startIfNeeded(providerClass)

  private fun startIfNeeded(providerClass: Class<out LspIntegrationProvider>) {
    if (!TrustedProjects.isProjectTrusted(project)) return

    val provider = findProvider(providerClass)
    if (provider == null) {
      logger.error(providerClass.name + " is not loaded")
      return
    }

    val requestStamp = startRequestStamp()
    cs.launch {
      val descriptorsToStart = readAction {
        val clients = getClients(providerClass)
        val descriptorsToStart = mutableListOf<LspClientDescriptor>()
        // a bulk provider may want one more client for a file within the roots of its other clients
        val bulk = provider is LspBulkIntegrationProvider

        for (file in FileEditorManager.getInstance(project).openFiles) {
          ProgressManager.checkCanceled()
          if (!ProjectFileIndex.getInstance(project).isInContent(file)) continue

          if (!bulk && clients.any { client ->
              client.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, file, true) }
            }) {
            // the file is already within the roots of a running server
            continue
          }

          if (!bulk && descriptorsToStart.any { descriptor ->
              descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, file, true) }
            }) {
            // the file is already within the roots of a server that will start soon
            continue
          }

          val starter = LspStarterImpl(provider)
          callFileOpened(provider, file, starter)
          descriptorsToStart.addAll(starter.descriptors)
        }
        descriptorsToStart
      }

      descriptorsToStart.forEach { ensureStarted(providerClass, it, requestStamp) }
    }
  }

  private fun findProvider(providerClass: Class<out LspIntegrationProvider>): LspIntegrationProvider? =
    LspIntegrationProvider.getAllExtensions().firstOrNull { it.javaClass == providerClass }

  private fun callFileOpened(provider: LspIntegrationProvider, file: VirtualFile, starter: LspStarterImpl): Unit =
    provider.fileOpened(project, file, starter)


  override fun ensureClientStarted(providerClass: Class<out LspIntegrationProvider>, descriptor: LspClientDescriptor) {
    ensureStarted(providerClass, descriptor)
  }

  @Deprecated("Use ensureClientStarted", ReplaceWith("ensureClientStarted(providerClass, descriptor)"))
  @Suppress("DEPRECATION", "UNCHECKED_CAST")
  override fun ensureServerStarted(providerClass: Class<out LspServerSupportProvider>, descriptor: LspServerDescriptor) {
    ensureStarted(providerClass as Class<out LspIntegrationProvider>, descriptor)
  }

  /**
   * [requestStamp] is the [startRequestStamp] value captured at the event that requested this start.
   * When [stopClients] for the provider, or [stopRunningServer] for the same server, came after the event,
   * the request is stale and the start is skipped.
   * `null` means an explicit request that always starts. Returns the started coroutine, so a test can join it.
   *
   * [replacing] is a stopped client that the new client replaces in its position in the list.
   * The start is skipped on a newer stop stamp, on [LspClientImpl.stopRequested], when [replacing] is no longer listed,
   * or when another client of the server is listed.
   * Then [replacing] stays listed and the IDE logs the reason. The client limit does not apply to a replacement.
   */
  internal fun ensureStarted(
    providerClass: Class<out LspIntegrationProvider>,
    descriptor: LspClientDescriptor,
    requestStamp: Long? = null,
    replacing: LspClientImpl? = null,
  ): Job? {
    if (!TrustedProjects.isProjectTrusted(project)) {
      replacing?.let { logDroppedAutoRestart(it, "the project is not trusted") }
      return null
    }

    val serverId = getServerId(providerClass, descriptor)
    return cs.launch {
      // make sure its listener is registered before the first `clientAdded` event, so the console gets all lifecycle events
      LspServiceViewSupport.getInstance(project)

      readAndEdtWriteAction {
        if (replacing == null && lspClients.any { it.getServerId() == serverId }) {
          return@readAndEdtWriteAction value(Unit)
        }

        if (replacing == null && lspClients.size >= MAX_LSP_CLIENTS) {
          logger.error("${lspClients.size} LSP servers are already running and one more wants to start." +
                       "To save system resources, this request will be ignored: $descriptor")
          return@readAndEdtWriteAction value(Unit)
        }

        writeAction {
          val client = synchronized(startStopLock) {
            val dropReason = when {
              requestStamp != null && lastStopStamp(providerClass, serverId) > requestStamp ->
                "an explicit stop came after the restart request"
              replacing?.stopRequested == true -> "the client was stopped on request"
              replacing != null && lspClients.none { it === replacing } -> "the client is no longer listed"
              lspClients.any { it !== replacing && it.getServerId() == serverId } -> "another client of the same server is listed"
              else -> null
            }
            if (dropReason != null) {
              replacing?.let { logDroppedAutoRestart(it, dropReason) }
              return@writeAction
            }
            val client = LspClientImpl(providerClass, descriptor, eventDispatcher.multicaster)
            client.start()
            if (replacing != null) {
              // the new client takes the position of the replaced client in one atomic change of the list
              lspClients.replaceAll { if (it === replacing) client else it }
            }
            else {
              lspClients.add(client)
            }
            client
          }
          // listeners run outside the lock
          if (replacing != null) eventDispatcher.multicaster.clientRemoved(replacing)
          eventDispatcher.multicaster.clientAdded(client)
        }
      }
    }
  }

  /**
   * The stamp for a start request. Capture it at the event that requests the start, before any scheduling.
   * [ensureStarted] drops the request when [stopClients] for the provider,
   * or [stopRunningServer] for the server, came after the captured stamp.
   */
  internal fun startRequestStamp(): Long = stopClock.get()

  private fun lastStopStamp(providerClass: Class<out LspIntegrationProvider>, serverId: String): Long =
    maxOf(lastStopStamps.getOrDefault(providerClass.name, 0L), lastStopStamps.getOrDefault(serverId, 0L))

  /**
   * Stops all clients of the provider.
   * The stop stamp and [LspClientImpl.stopRequested] are set in one step under [startStopLock].
   */
  override fun stopClients(providerClass: Class<out LspIntegrationProvider>) {
    val clients = synchronized(startStopLock) {
      lastStopStamps[providerClass.name] = stopClock.incrementAndGet()
      getClients(providerClass).onEach { it.stopRequested = true }
    }
    clients.forEach { stopRunningServer(it) }
  }

  @Deprecated("Use stopClients", ReplaceWith("stopClients(providerClass)"))
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "UNCHECKED_CAST")
  override fun stopServers(providerClass: Class<out LspServerSupportProvider>): Unit =
    stopClients(providerClass as Class<out LspIntegrationProvider>)

  /**
   * Called when the server works fine but needs to be stopped for some reason.
   * For example, an action like `Stop server` or `Restart server` is invoked, or the project is closed.
   * The stamp is keyed by the server id, so the stop does not drop a queued sibling start of the same provider.
   *
   * [lspClient] can be a client that is no longer listed.
   * The call also cancels the pending automatic restart of the server and clears its restart count.
   */
  internal fun stopRunningServer(lspClient: LspClientImpl) {
    val serverId = lspClient.getServerId()
    synchronized(startStopLock) {
      lspClient.stopRequested = true
      lastStopStamps[serverId] = stopClock.incrementAndGet()
      pendingAutoRestarts.remove(serverId)?.cancel()
      autoRestartLimit.clear(serverId)
    }
    lspClient.ensureServerStopped(explicitStop = true) {
      handleServerStop(lspClient, explicitStop = true)
    }
  }

  /**
   * Stops [lspClient] and starts a new client with the same descriptor.
   * The restart must not depend on the open-editors scan of [startIfNeeded]: the scan skips files outside the local
   * file system, so it revives no server when only a content file tab (see [LspDynamicFiles]) remains open.
   */
  internal fun restartClient(lspClient: LspClientImpl) {
    stopRunningServer(lspClient)
    ensureStarted(lspClient.providerClass, lspClient.descriptor)
  }

  /**
   * Called when the IDE detects that the server stopped working, for example,
   * the server process has terminated or the socket connection has been lost.
   * 1. This might be expected because the server has recently been stopped by calling [stopRunningServer].
   * In this case, this function doesn't do anything.
   * 2. Otherwise, the server termination is treated as an unexpected one.
   *
   * [serverGone] is `true` when the server process ended or the connection to the server closed.
   * It is `false` for a failure in the IDE during the start of the client.
   * Only the report that stops the client decides on an automatic restart.
   * With [serverGone] and a client that was in the Running state, [scheduleAutoRestart] can start a new client.
   * When the stop throws an exception, the scheduling runs only if the server was released, and the exception is rethrown.
   */
  internal fun handleMaybeUnexpectedServerStop(lspClient: LspClientImpl, serverOutput: String, serverGone: Boolean) {
    var wasRunning = false
    var stopped = false
    runSuppressing(
      {
        lspClient.ensureServerStopped(explicitStop = false) {
          // runs under the state lock of the client, before the state changes; a repeated report sees a Shutdown* state
          val running = lspClient.state == LspServerState.Running
          if (lspClient.state != LspServerState.ShutdownNormally) lspClient.appendServerErrorOutput(serverOutput)
          handleServerStop(lspClient, explicitStop = false)
          // set last: an exception above leaves the client in its state, so no restart is scheduled
          wasRunning = running
        }
        stopped = true
      },
      {
        // a stop that threw before the release would leave the restart waiting for `disconnected` forever
        if (serverGone && wasRunning) {
          if (stopped || lspClient.disconnected.isCompleted) {
            scheduleAutoRestart(lspClient)
          }
          else {
            logDroppedAutoRestart(lspClient, "the stop of the client failed before the release of the server")
          }
        }
      },
    )
  }

  /**
   * Starts a new client for [lspClient] after a delay.
   * The server of [lspClient] stopped unexpectedly while the client was running.
   * The call does nothing for a client with [LspClientImpl.stopRequested].
   * [LspAutoRestartLimit] sets the delay and can refuse the restart, for example, when
   * [LspClientDescriptor.maxUnexpectedTerminationRestarts] is 0 or less.
   * The restart is a start request with the stamp of this call, and it never writes a stop stamp.
   */
  private fun scheduleAutoRestart(lspClient: LspClientImpl) {
    if (project.isDisposed) return
    val serverId = lspClient.getServerId()
    val descriptorMaxRestarts = lspClient.descriptor.maxUnexpectedTerminationRestarts
    val decision = synchronized(startStopLock) {
      if (lspClient.stopRequested) {
        null
      }
      else {
        autoRestartLimit.grant(serverId, descriptorMaxRestarts).also {
          if (it is LspAutoRestartLimit.Decision.Granted) launchAutoRestart(lspClient, serverId, it.delay, startRequestStamp())
        }
      }
    }
    when (decision) {
      null -> logDroppedAutoRestart(lspClient, "the client was stopped on request")
      is LspAutoRestartLimit.Decision.Granted ->
        lspClient.logInfo("The LSP server stopped unexpectedly. The IDE starts it again in ${decision.delay}.")
      LspAutoRestartLimit.Decision.Refused ->
        lspClient.logWarn("The LSP server stopped unexpectedly too often. The IDE does not start it again.")
      LspAutoRestartLimit.Decision.Disabled -> Unit
    }
  }

  /**
   * The restart waits for [LspClientImpl.disconnected], so the IDE releases the stopped server before the new client starts.
   * When the release fails, the restart does not occur.
   * The job stays pending until the start of the new client commits or is dropped.
   * A cancellation of the job stops only the wait. The checks in [ensureStarted] drop a start that has already begun.
   */
  private fun launchAutoRestart(lspClient: LspClientImpl, serverId: String, restartDelay: Duration, requestStamp: Long) {
    val job = cs.launch(start = CoroutineStart.LAZY) {
      delay(restartDelay)
      try {
        lspClient.disconnected.await()
      }
      catch (e: Throwable) {
        coroutineContext.ensureActive() // the restart itself was cancelled
        val cause = e.takeUnless { it is ControlFlowException }
        logDroppedAutoRestart(lspClient, "the IDE could not release the stopped server", cause)
        return@launch
      }
      restartAfterUnexpectedStop(lspClient, requestStamp)?.join()
    }
    // a previous job of the server id has committed its client and is completing; cancelling it is harmless
    pendingAutoRestarts.put(serverId, job)?.cancel()
    job.invokeOnCompletion { pendingAutoRestarts.remove(serverId, job) }
    job.start()
  }

  /**
   * Replaces the stopped [lspClient] with a new client that has the same descriptor, unless [ensureStarted] skips the start.
   * [requestStamp] is the stamp of the start request from [scheduleAutoRestart].
   * Returns the started coroutine, so a test can join it.
   */
  internal fun restartAfterUnexpectedStop(lspClient: LspClientImpl, requestStamp: Long): Job? {
    val providerClass = lspClient.providerClass
    if (findProvider(providerClass) == null) {
      logDroppedAutoRestart(lspClient, "the integration provider is unloaded")
      return null
    }
    return ensureStarted(providerClass, lspClient.descriptor, requestStamp, replacing = lspClient)
  }

  private fun logDroppedAutoRestart(lspClient: LspClientImpl, reason: String, cause: Throwable? = null) {
    val message = "The IDE does not start the LSP server again, because $reason."
    if (cause == null) lspClient.logInfo(message) else lspClient.logWarn(message, cause)
  }

  @TestOnly
  internal fun pendingAutoRestartJobs(): Collection<Job> = pendingAutoRestarts.values.toList()

  /**
   * Called from [stopRunningServer] and from [handleMaybeUnexpectedServerStop]. Not expected to be called from anywhere else.
   * @param explicitStop
   * - `true` is passed by [stopRunningServer], which is called when the server is working fine but needs to be stopped for some reason
   * - `false` is passed by [handleMaybeUnexpectedServerStop].
   * It is called when the IDE detects that the server stopped working, which may be both expected and unexpected
   */
  private fun handleServerStop(lspClient: LspClientImpl, explicitStop: Boolean) {
    if (lspClient.state in arrayOf(LspServerState.Initializing, LspServerState.Running) && !lspClients.contains(lspClient)) {
      logger.error("LspClientManager doesn't know the server that it is asked to stop: $lspClient")
    }

    if (explicitStop) {
      // The serverState might be already ShutdownUnexpectedly at this point.
      // `explicitStop == true` for a server that is already ShutdownUnexpectedly means one of the following:
      // - project closed,
      // - plugin unloaded,
      // - plugin-specific technology disabled in Settings (stopServers(providerClass) called),
      // - manual server restart (RestartLspServerAction).
      // In any case, we need to remove it from the `lspClients` collection
      if (lspClients.remove(lspClient)) {
        eventDispatcher.multicaster.clientRemoved(lspClient)
      }
    }
    else {
      // ShutdownUnexpectedly servers stay in the `lspClients` collection so that they show up as 'Terminated' in the status bar widget.
      // `scheduleAutoRestart` replaces such a client with a new client only when its descriptor allows automatic restarts
      // (`maxUnexpectedTerminationRestarts`).
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

  internal fun wrapLsp4jServer(lspClient: LspClientImpl, lsp4jServer: Lsp4jServer): Lsp4jServer =
    @Suppress("TestOnlyProblems")
    lsp4jServerWrappers.fold(lsp4jServer) { wrappedLsp4jServer, wrapper ->
      wrapper.wrapLsp4jServer(lspClient, wrappedLsp4jServer)
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
    // The current implementation handles the following use cases:
    // - some file is open in the editor, but it doesn't belong to the project (for example, it is in an excluded folder)
    // - later this file becomes a project file for some reason (for example, its parent folder is unexcluded)
    // In this case, it might be needed to send `didOpen` request for this file (if there's an already running LSP server that wants to handle it),
    // or it might be needed to start an LSP server that wants to handle this file.
    // And the reverse one: a file that a server has opened becomes excluded, so the server gets `didClose` for it.

    // TODO Some running servers might need to update the roots they serve (`didChangeWorkspaceFolders` notification)
    // TODO Some running servers might need to be stopped if they serve roots that don't belong to the project anymore

    readAction {
      val openedFiles = FileEditorManager.getInstance(project).openFiles.filterNotNull()
      val unsavedFiles = FileDocumentManager.getInstance().unsavedDocuments.mapNotNull { FileDocumentManager.getInstance().getFile(it) }
      LspOpenedFilesService.getInstance(project).processOpenedFiles(unsavedFiles + openedFiles)
    }
    LspOpenedFilesService.getInstance(project).scheduleClosingFilesThatAreNotOfInterest()
  }

  override fun dispose(): Unit = lspClients.forEach { stopRunningServer(it) }


  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  internal class LspStarterImpl(private val provider: LspIntegrationProvider) :
    LspIntegrationProvider.LspClientStarter, LspServerSupportProvider.LspServerStarter {
    /**
     * An [LspBulkIntegrationProvider] gets a client per passed descriptor.
     * Any other provider runs one client for the file, so its last passed descriptor wins.
     */
    val descriptors = mutableListOf<LspClientDescriptor>()

    override fun ensureClientStarted(descriptor: LspClientDescriptor) {
      if (provider !is LspBulkIntegrationProvider) descriptors.clear()
      descriptors.add(descriptor)
    }

    override fun ensureServerStarted(descriptor: LspServerDescriptor) {
      ensureClientStarted(descriptor)
    }
  }


  companion object {
    fun getInstanceImpl(project: Project): LspClientManagerImpl = LspClientManager.getInstance(project) as LspClientManagerImpl

    internal inline fun forEachRunningClientInEachProject(file:VirtualFile?=null, action: (LspClientImpl) -> Unit) {
      ProjectManager.getInstance().openProjects.forEach { project ->
        getInstanceImpl(project).lspClients.forEach { client ->
          if (client.state == LspServerState.Running &&
              (file == null || client.isFileOpened(file) && client.isSupportedFile(file))) {
            action(client)
          }
        }
      }
    }

    internal fun isAnyServerRunning(): Boolean =
      ProjectManager.getInstance().openProjects.any { project ->
        getInstanceImpl(project).lspClients.any { it.state == LspServerState.Running }
      }
  }
}
