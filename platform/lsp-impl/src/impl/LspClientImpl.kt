package com.intellij.platform.lsp.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.api.LspCommunicationChannel.StdIO
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersSupport
import com.intellij.platform.lsp.impl.connector.Lsp4jServerConnector
import com.intellij.platform.lsp.impl.connector.Lsp4jServerConnectorSocket
import com.intellij.platform.lsp.impl.connector.Lsp4jServerConnectorStdio
import com.intellij.platform.lsp.impl.connector.LspInitializationException
import com.intellij.platform.lsp.impl.documentSync.LspDocumentSyncManager
import com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing
import com.intellij.platform.lsp.impl.features.highlighting.DiagnosticAndQuickFixes
import com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCacheRegistry
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayApplier
import com.intellij.platform.lsp.impl.features.navigation.LspDynamicFiles
import com.intellij.platform.lsp.impl.fileEvents.LspWatchedFiles
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.nullize
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.util.Collections
import java.util.concurrent.CompletableFuture
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private val logger = logger<LspClientImpl>()


@ApiStatus.Internal
class LspClientImpl internal constructor(
  override val providerClass: Class<out LspIntegrationProvider>,
  override val descriptor: LspClientDescriptor,
  private val eventBroadcaster: LspClientManagerListener,
) : @Suppress("TYPEALIAS_EXPANSION_DEPRECATION") LspClientRenameCompat {
  override val project: Project = descriptor.project

  override var state: LspServerState = LspServerState.Initializing
    private set(value) {
      if (value == LspServerState.Initializing ||
          (field != LspServerState.Initializing && value == LspServerState.Running) ||
          field == LspServerState.ShutdownNormally ||
          field == LspServerState.ShutdownUnexpectedly) {
        logger.error("Incorrect state change: $field -> $value")
        return
      }
      field = value
      eventBroadcaster.serverStateChanged(this)
    }
  private val stateLock = Any()

  override var initializeResult: InitializeResult? = null
    private set

  val documentMapping: LspDocumentMapping = LspDocumentMapping(this)
  val requestExecutor: LspRequestExecutor = LspRequestExecutor(this, documentMapping)
  internal val globMatcher: LspGlobMatcher = LspGlobMatcher()
  internal val dynamicCapabilities: LspDynamicCapabilities = LspDynamicCapabilities()
  internal val serverNotificationsHandler: LspServerNotificationsHandlerImpl = LspServerNotificationsHandlerImpl(this)

  internal val documentSyncManager = LspDocumentSyncManager(this)
  internal val watchedFiles = LspWatchedFiles(this)
  internal val dynamicFiles = LspDynamicFiles(this)
  private val unsupportedFilePaths: MutableSet<String> = Collections.synchronizedSet(HashSet())
  internal val highlightingCacheRegistry = LspHighlightingCacheRegistry(this)

  private lateinit var lsp4jServerConnector: Lsp4jServerConnector
  private val connectorLock = Any()

  private val errorOutputBuffer: StringBuilder = StringBuilder()
  internal val errorOutput: String?
    get() = synchronized(errorOutputBuffer) { errorOutputBuffer.toString() }.nullize()

  @get:ApiStatus.Internal
  val lsp4jServer: Lsp4jServer
    get() = lsp4jServerConnector.lsp4jServer

  internal val serverCapabilities: ServerCapabilities?
    get() = if (state == LspServerState.Running) initializeResult?.capabilities else null

  internal fun isFileOpened(file: VirtualFile): Boolean = documentSyncManager.isFileOpened(file)

  internal fun forEachOpenedFile(action: (VirtualFile) -> Unit) = documentSyncManager.forEachOpenedFile(action)

  internal fun notifyFileOpened(file: VirtualFile) {
    eventBroadcaster.fileOpened(this, file)
  }

  override fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit): Unit =
    requestExecutor.sendNotification(lsp4jSender)

  override suspend fun <Lsp4jResponse> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>): Lsp4jResponse? =
    requestExecutor.sendRequest(lsp4jSender)

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  override fun <Lsp4jResponse> sendRequestSync(
    timeoutMs: Int,
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
  ): Lsp4jResponse? =
    requestExecutor.sendRequestSync(timeoutMs, lsp4jSender)

  override fun getDocumentIdentifier(file: VirtualFile): TextDocumentIdentifier =
    TextDocumentIdentifier(descriptor.getFileUri(file))

  override fun getDocumentVersion(document: Document): Int {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return -1
    return documentSyncManager.currentDocumentVersion(file)
  }

  override fun nextDocumentVersion(document: Document): Int {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return -1
    return documentSyncManager.nextDocumentVersion(file)
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  internal fun isSupportedFile(file: VirtualFile): Boolean {
    if (unsupportedFilePaths.contains(file.path)) return false

    return descriptor.isSupportedFile(file)
      .also { if (!it) unsupportedFilePaths.add(file.path) }
  }

  internal fun diagnosticsReceived(params: PublishDiagnosticsParams) {
    highlightingCacheRegistry.publishDiagnosticsCache.diagnosticsReceived(params)
  }

  internal fun fileEdited(file: VirtualFile, e: DocumentEvent) {
    highlightingCacheRegistry.fileEdited(file, e)
    eventBroadcaster.fileEdited(this, file)
    if (isFileOpened(file)) {
      // Re-apply the cached highlightings with the edit-adjusted ranges. Without this, highlightings
      // applied before the edit keep their pre-edit offsets until the next daemon pass.
      LspHighlightingApplier.getInstance(project).scheduleHighlightingRefreshDebounced(file)
    }
  }

  internal fun fileClosed(file: VirtualFile) {
    highlightingCacheRegistry.fileClosed(file)
  }

  override fun invalidateServerResults() {
    requestExecutor.clearCaches()
    forEachOpenedFile { file ->
      highlightingCacheRegistry.invalidatePulledResults(file)
      LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
      LspInlayApplier.getInstance(project).scheduleRefresh(file)
    }
    if (!project.isDisposed) {
      LspFeaturesRefreshing.refreshCodeLenses(project)
    }
  }

  internal fun refreshSemanticTokens() {
    highlightingCacheRegistry.semanticTokensCache.clearCache()
    forEachOpenedFile { file ->
      LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
    }
  }

  /**
   * Handles a server-forced `workspace/inlayHint/refresh`: re-requests inlay hints for every opened file even without
   * a document edit, then re-applies out-of-band. Invalidating the cache keeps the current hints on screen (no
   * flicker); [LspInlayApplier.scheduleRefresh] kicks the re-request and diffs in the fresh hints once they land.
   */
  internal fun refreshInlayHints() {
    forEachOpenedFile { file ->
      highlightingCacheRegistry.inlayHintsCache.invalidate(file)
      LspInlayApplier.getInstance(project).scheduleRefresh(file)
    }
  }

  /**
   * Handles a server-forced `workspace/diagnostic/refresh`, and a server re-registering `textDocument/diagnostic`:
   * both mean the diagnostics the IDE pulled no longer reflect what the server would answer now. Invalidating the
   * cache keeps the current diagnostics on screen (no flicker);
   * [LspHighlightingApplier.scheduleHighlightingRefresh] kicks the re-pull and applies the fresh results once they land.
   */
  internal fun refreshDiagnostics() {
    forEachOpenedFile { file ->
      highlightingCacheRegistry.pullDiagnosticsCache.forceFullRepull(file)
      LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
    }
  }

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @VisibleForTesting
  fun getDiagnosticsAndQuickFixes(file: VirtualFile): List<DiagnosticAndQuickFixes> =
    highlightingCacheRegistry.getDiagnosticsAndQuickFixes(file)

  internal fun notifyDocumentLinksReceived(file: VirtualFile) = eventBroadcaster.documentLinksReceived(this, file)

  internal fun notifyDiagnosticsReceived(file: VirtualFile) {
    eventBroadcaster.diagnosticsReceived(this, file)
  }

  internal fun start() {
    if (!TrustedProjects.isProjectTrusted(project)) {
      // This check is added for safety. This method must not be called for unrusted projects
      throw IllegalStateException("Project is not trusted")
    }

    if (state != LspServerState.Initializing) {
      logError("start() cannot be called for a server twice")
      return
    }

    logInfo("Starting LSP server")
    val startTime = TimeSource.Monotonic.markNow()

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        synchronized(connectorLock) {
          lsp4jServerConnector = createLsp4jServerConnector()
          lsp4jServerConnector.connect { initializeResult ->
            this.initializeResult = initializeResult

            // While waiting for the server initialization, the `state` might have already become `ShutdownNormally`
            // or `ShutdownUnexpectedly`, which means that the server is going to shut down shortly, so it must not get the Running state
            synchronized(stateLock) {
              if (state == LspServerState.Initializing) state = LspServerState.Running
            }

            val message = "LSP server initialized in ${startTime.elapsedNow().toString(DurationUnit.SECONDS, 3)}"
            logInfo(initializeResult.serverInfo?.let { "$message, name = ${it.name}, version = ${it.version}" }
                    ?: message)
            descriptor.lspServerListener?.serverInitialized(initializeResult)
          }
        }
        documentSyncManager.openForOpenedOrUnsavedFiles()
        LspFeaturesRefreshing.refreshBreadcrumbs()
        forEachOpenedFile { LspInlayApplier.getInstance(project).scheduleRefresh(it) }
        LspFeaturesRefreshing.refreshCodeLenses(project)
      }
      catch (e: Exception) {
        // stack trace of the LspInitializationException is always the same, so not interesting; let's log its cause
        val exToLog = (e as? LspInitializationException)?.cause ?: e
        if (e is AlreadyDisposedException) {
          // The project is disposed
          ensureServerStopped(false) {}
          return@executeOnPooledThread
        }
        logWarn("Failed to start LSP server", exToLog)

        val manager = ReadAction.computeBlocking<LspClientManagerImpl?, Throwable> {
          if (!project.isDisposed) LspClientManagerImpl.getInstanceImpl(project) else null
        }
        val text = (if (e is LspInitializationException) "$e\nCaused by:\n" else "") + exToLog.stackTraceToString()
        manager?.handleMaybeUnexpectedServerStop(this, text)
      }
    }
  }

  private fun createLsp4jServerConnector(): Lsp4jServerConnector = when (descriptor.lspCommunicationChannel) {
    is StdIO -> Lsp4jServerConnectorStdio(this)
    is LspCommunicationChannel.Socket -> Lsp4jServerConnectorSocket(this)
  }

  internal fun ensureServerStopped(explicitStop: Boolean, updateLspServerManagerState: () -> Unit) {
    synchronized(stateLock) {
      updateLspServerManagerState()

      if (state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) return // already shut down

      logInfo("Stopping LSP server ${if (explicitStop) "normally" else "unexpectedly"}")
      state = if (explicitStop) LspServerState.ShutdownNormally else LspServerState.ShutdownUnexpectedly

      if (!project.isDisposed) {
        val inheritanceMarkersEnabled = descriptor.lspCustomization.inheritanceMarkersCustomizer is LspInheritanceMarkersSupport
        forEachOpenedFile { file ->
          LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
          LspInlayApplier.getInstance(project).scheduleRefresh(file)
          if (inheritanceMarkersEnabled) {
            LspFeaturesRefreshing.refreshLineMarkers(project, file)
          }
        }
      }
      documentSyncManager.shutdown()
      requestExecutor.shutdownNow()
      serverNotificationsHandler.cancelAllProgress()
      serverNotificationsHandler.releasePendingMessageRequests()

      highlightingCacheRegistry.clearCache()

      if (!project.isDisposed) {
        LspFeaturesRefreshing.refreshCodeLenses(project)
      }
    }

    // A graceful `shutdown`/`exit` handshake only makes sense for an explicit stop of a still-responsive server.
    // On an unexpected stop the server-to-IDE channel is already dead, so skip the handshake and just disconnect.
    shutdownAndExit(graceful = explicitStop)
  }

  private fun shutdownAndExit(graceful: Boolean) {
    val shutdownAndExit = Runnable {
      synchronized(connectorLock) {
        if (::lsp4jServerConnector.isInitialized) lsp4jServerConnector.shutdownExitDisconnect(graceful)
      }
    }

    if (ApplicationManager.getApplication().isDispatchThread || ApplicationManager.getApplication().isReadAccessAllowed) {
      ApplicationManager.getApplication().executeOnPooledThread(shutdownAndExit)
    }
    else {
      shutdownAndExit.run()
    }
  }

  internal fun appendServerErrorOutput(text: String): Unit = synchronized(errorOutputBuffer) {
    if (!errorOutputBuffer.isEmpty()) errorOutputBuffer.append("\n")
    when {
      text.length > MAX_ERROR_OUTPUT_SIZE -> {
        errorOutputBuffer.replace(0, errorOutputBuffer.length, text.substring(text.length - MAX_ERROR_OUTPUT_SIZE))
      }
      errorOutputBuffer.length + text.length > MAX_ERROR_OUTPUT_SIZE -> {
        errorOutputBuffer.delete(0, errorOutputBuffer.length + text.length - MAX_ERROR_OUTPUT_SIZE)
        errorOutputBuffer.append(text)
      }
      else -> errorOutputBuffer.append(text)
    }
  }

  override fun toString(): String = "$descriptor($state;${documentSyncManager.openedFileCount})"

  internal fun logDebug(message: @NonNls String) = logger.debug(decorateLogMessage(message))
  internal fun logInfo(message: @NonNls String) = logger.info(decorateLogMessage(message))
  internal fun logWarn(message: @NonNls String, t: Throwable? = null) = logger.warn(decorateLogMessage(message), t)
  internal fun logError(message: @NonNls String) = logger.error(decorateLogMessage(message))
  private fun decorateLogMessage(message: String): String = "$this: $message"

  companion object {
    internal const val NOT_CANCELLABLE_REQUEST_TIMEOUT_MS: Int = 300
    private const val MAX_ERROR_OUTPUT_SIZE: Int = FileUtilRt.MEGABYTE
  }
}
