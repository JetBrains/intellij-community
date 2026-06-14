package com.intellij.platform.lsp.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.api.LspCommunicationChannel.StdIO
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.connector.Lsp4jServerConnector
import com.intellij.platform.lsp.impl.connector.Lsp4jServerConnectorSocket
import com.intellij.platform.lsp.impl.connector.Lsp4jServerConnectorStdio
import com.intellij.platform.lsp.impl.connector.LspInitializationException
import com.intellij.platform.lsp.impl.documentSync.LspDocumentSyncManager
import com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing
import com.intellij.platform.lsp.impl.features.highlighting.DiagnosticAndQuickFixes
import com.intellij.platform.lsp.impl.features.highlighting.LspDocumentLink
import com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayApplier
import com.intellij.platform.lsp.impl.features.highlighting.LspSemanticToken
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspCachedHighlighting
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCacheRegistry
import com.intellij.platform.lsp.impl.fileEvents.LspWatchedFiles
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.nullize
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.SaveOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
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
  internal val serverNotificationsHandler: LspServerNotificationsHandler = LspServerNotificationsHandlerImpl(this)

  internal val documentSyncManager = LspDocumentSyncManager(this)
  internal val watchedFiles = LspWatchedFiles(this)
  private val unsupportedFilePaths: MutableSet<String> = Collections.synchronizedSet(HashSet())
  private val highlightingCacheRegistry = LspHighlightingCacheRegistry(this)

  private lateinit var lsp4jServerConnector: Lsp4jServerConnector
  private val connectorLock = Any()

  private val errorOutputBuffer: StringBuilder = StringBuilder()
  internal val errorOutput: String?
    get() = errorOutputBuffer.toString().nullize()

  internal val lsp4jServer: Lsp4jServer
    get() = lsp4jServerConnector.lsp4jServer

  internal val serverCapabilities: ServerCapabilities?
    get() = if (state == LspServerState.Running) initializeResult?.capabilities else null

  internal val textDocumentSyncKind: TextDocumentSyncKind?
    @Suppress("RemoveExplicitTypeArguments")
    get() = serverCapabilities?.textDocumentSync?.map<TextDocumentSyncKind?>({ it }, { it.change })

  internal fun isFileOpened(file: VirtualFile): Boolean = documentSyncManager.isFileOpened(file)

  internal fun forEachOpenedFile(action: (VirtualFile) -> Unit) = documentSyncManager.forEachOpenedFile(action)

  internal fun notifyFileOpened(file: VirtualFile) {
    eventBroadcaster.fileOpened(this, file)
  }

  override fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit): Unit =
    requestExecutor.sendNotification(lsp4jSender)

  override suspend fun <Lsp4jResponse> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>): Lsp4jResponse? =
    requestExecutor.sendRequest(lsp4jSender)

  @RequiresBackgroundThread
  override fun <Lsp4jResponse> sendRequestSync(
    timeoutMs: Int,
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
  ): Lsp4jResponse? =
    requestExecutor.sendRequestSync(timeoutMs, lsp4jSender)

  override fun getDocumentIdentifier(file: VirtualFile): TextDocumentIdentifier =
    TextDocumentIdentifier(descriptor.getFileUri(file))

  override fun getDocumentVersion(document: Document): Int =
    (document as? DocumentEx)?.modificationSequence ?: document.modificationStamp.toInt()

  @RequiresReadLock
  @RequiresBackgroundThread
  internal fun isSupportedFile(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem) return false
    if (unsupportedFilePaths.contains(file.path)) return false
    if (!ProjectFileIndex.getInstance(project).isInContent(file)) return false

    return descriptor.isSupportedFile(file)
      .also { if (!it) unsupportedFilePaths.add(file.path) }
  }

  internal fun diagnosticsReceived(params: PublishDiagnosticsParams) {
    highlightingCacheRegistry.publishDiagnosticsCache.diagnosticsReceived(params)
  }

  internal fun fileEdited(file: VirtualFile, e: DocumentEvent) {
    highlightingCacheRegistry.fileEdited(file, e)
    eventBroadcaster.fileEdited(this, file)
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

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getSemanticTokens(file: VirtualFile): List<LspCachedHighlighting<LspSemanticToken>> =
    highlightingCacheRegistry.semanticTokensCache.getHighlightings(file)

  @RequiresBackgroundThread
  fun getDiagnosticsAndQuickFixes(file: VirtualFile): List<DiagnosticAndQuickFixes> =
    highlightingCacheRegistry.getDiagnosticsAndQuickFixes(file)

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getColorInfos(file: VirtualFile): List<LspCachedHighlighting<Color>> =
    highlightingCacheRegistry.documentColorCache.getHighlightings(file)

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getDocumentLinkInfos(file: VirtualFile): List<LspCachedHighlighting<LspDocumentLink>> =
    highlightingCacheRegistry.documentLinkCache.getHighlightings(file)

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getFoldingRangeInfos(file: VirtualFile): List<LspCachedHighlighting<FoldingRange>> =
    highlightingCacheRegistry.foldingRangeCache.getHighlightings(file)

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getInlayHints(file: VirtualFile): List<LspCachedHighlighting<InlayHint>> =
    highlightingCacheRegistry.inlayHintsCache.getHighlightings(file)

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getCodeLens(file: VirtualFile): List<LspCachedHighlighting<CodeLens>> =
    highlightingCacheRegistry.codeLensCache.getHighlightings(file)

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

        val lspServerManager = ReadAction.computeBlocking<LspClientManagerImpl?, Throwable> {
          if (!project.isDisposed) LspClientManagerImpl.getInstanceImpl(project) else null
        }
        val text = (if (e is LspInitializationException) "$e\nCaused by:\n" else "") + exToLog.stackTraceToString()
        lspServerManager?.handleMaybeUnexpectedServerStop(this, text)
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

      forEachOpenedFile { file ->
        LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
        LspInlayApplier.getInstance(project).scheduleRefresh(file)
      }
      documentSyncManager.clearOpenedFiles()
      requestExecutor.shutdownNow()

      highlightingCacheRegistry.clearCache()

      if (!project.isDisposed) {
        LspFeaturesRefreshing.refreshCodeLenses(project)
      }
    }

    shutdownAndExit()
  }

  private fun shutdownAndExit() {
    val shutdownAndExit = Runnable {
      synchronized(connectorLock) {
        if (::lsp4jServerConnector.isInitialized) lsp4jServerConnector.shutdownExitDisconnect()
      }
    }

    if (ApplicationManager.getApplication().isDispatchThread || ApplicationManager.getApplication().isReadAccessAllowed) {
      ApplicationManager.getApplication().executeOnPooledThread(shutdownAndExit)
    }
    else {
      shutdownAndExit.run()
    }
  }

  internal fun supportsPullDiagnostics(file: VirtualFile): Boolean =
    serverCapabilities?.diagnosticProvider != null ||
    hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.diagnostic)

  internal fun supportsDocumentColor(file: VirtualFile): Boolean =
    serverCapabilities?.colorProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentColor)

  internal fun supportsDocumentLink(file: VirtualFile): Boolean =
    serverCapabilities?.documentLinkProvider != null ||
    hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentLink)

  internal fun supportsDocumentSymbol(file: VirtualFile): Boolean =
    serverCapabilities?.documentSymbolProvider != null ||
    hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentSymbol)

  internal fun supportsFoldingRange(file: VirtualFile): Boolean =
    serverCapabilities?.foldingRangeProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.foldingRange)

  internal fun supportsCodeActions(codeActionKindsFilter: (List<String>) -> Boolean): Boolean =
    serverCapabilities?.codeActionProvider?.let {
      if (it.isLeft) return it.left!!
      val kinds = it.right!!.codeActionKinds
      if (kinds == null) return true // well, the server doesn't have to list code action kinds explicitly, but it does support them!
      return codeActionKindsFilter(kinds)
    }
    ?: false

  internal fun supportsGotoDefinition(): Boolean = serverCapabilities?.definitionProvider?.let { it.left ?: true } == true

  internal fun supportsGotoTypeDefinition(): Boolean = serverCapabilities?.typeDefinitionProvider?.let { it.left ?: true } == true

  internal fun supportsHover(): Boolean = serverCapabilities?.hoverProvider?.let { it.left ?: true } == true

  internal fun supportsFindReferences(file: VirtualFile): Boolean =
    serverCapabilities?.referencesProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.references)

  internal fun supportsInlayHints(file: VirtualFile): Boolean =
    serverCapabilities?.inlayHintProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.inlayHint)

  internal fun supportsDocumentHighlights(file: VirtualFile): Boolean =
    serverCapabilities?.documentHighlightProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentHighlight)

  internal fun supportsGoToSymbol(): Boolean = serverCapabilities?.workspaceSymbolProvider?.let { it.left ?: true } == true
                                               || dynamicCapabilities.hasCapability(LspDynamicCapabilities.symbol)

  internal fun supportsSignatureHelp(file: VirtualFile): Boolean =
    serverCapabilities?.signatureHelpProvider != null ||
    hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.signatureHelp)

  internal fun supportsCallHierarchy(file: VirtualFile): Boolean =
    serverCapabilities?.callHierarchyProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.prepareCallHierarchy)

  internal fun supportsTypeHierarchy(file: VirtualFile): Boolean =
    serverCapabilities?.typeHierarchyProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.prepareTypeHierarchy)

  internal fun supportsSelectionRange(file: VirtualFile): Boolean =
    serverCapabilities?.selectionRangeProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.selectionRange)

  internal fun supportsCodeLens(file: VirtualFile): Boolean =
    serverCapabilities?.codeLensProvider != null ||
    hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.codeLens)

  internal fun supportsRename(file: VirtualFile): Boolean =
    serverCapabilities?.renameProvider?.let { it.left ?: true }
    ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.rename)

  internal fun supportsPrepareRename(file: VirtualFile): Boolean {
    return serverCapabilities?.renameProvider?.right?.prepareProvider == true ||
           getDynamicCapabilityOptionsForFile(file, LspDynamicCapabilities.rename)?.prepareProvider == true
  }

  internal fun getDidSaveOptions(file: VirtualFile): SaveOptions? {
    val textDocumentSync = serverCapabilities?.textDocumentSync
    if (textDocumentSync?.right?.save?.left == true) return SaveOptions(false)
    textDocumentSync?.right?.save?.right?.let { return it }
    // According to https://microsoft.github.io/language-server-protocol/specification/#textDocument_synchronization
    // `textDocumentSync` can be `TextDocumentSyncOptions` or `TextDocumentSyncKind`. When it is `TextDocumentSyncKind` there are no rules
    // that the client must send didSave notification, but VS Code does it in the case of TextDocumentSyncKind.Incremental and
    // TextDocumentSyncKind.Full. As good practice we should follow VS Code behavior.
    if (textDocumentSync?.left != null && textDocumentSync.left != TextDocumentSyncKind.None) return SaveOptions(false)
    getDynamicCapabilityOptionsForFile(file, LspDynamicCapabilities.didSave)?.let { return SaveOptions(it.includeText) }
    return null
  }

  /**
   * @return `true` if the server says it is able to format at least some files;
   * `false` if the server doesn't support code formatting at all
   * @see LspClientImpl.doesServerExplicitlyWantToFormatThisFile
   */
  internal fun hasFullFileFormattingCapability(): Boolean =
    dynamicCapabilities.hasCapability(LspDynamicCapabilities.formatting) ||
    serverCapabilities?.documentFormattingProvider?.let { it.left ?: true } == true


  /**
   * @return `true` if the server says it is able to format at least some files;
   * `false` if the server doesn't support code range formatting at all
   * @see LspClientImpl.doesServerExplicitlyWantToFormatThisFile
   */
  internal fun hasRangeFormattingCapability(): Boolean =
    dynamicCapabilities.hasCapability(LspDynamicCapabilities.rangeFormatting) ||
    serverCapabilities?.documentRangeFormattingProvider?.let { it.left ?: true } == true

  /**
   * See docs for the `serverExplicitlyWantsToFormatThisFile` parameter in
   * [com.intellij.platform.lsp.api.customization.LspFormattingSupport.shouldFormatThisFileExclusivelyByServer]
   */
  internal fun doesServerExplicitlyWantToFormatThisFile(file: VirtualFile, isFullFileFormatting: Boolean): Boolean {
    @Suppress("UNCHECKED_CAST")
    val capabilityAndOptionsClass: Pair<String, Class<TextDocumentRegistrationOptions>> =
      (if (isFullFileFormatting) LspDynamicCapabilities.formatting else LspDynamicCapabilities.rangeFormatting)
        as Pair<String, Class<TextDocumentRegistrationOptions>>
    // We intentionally don't check static server capabilities (serverCapabilities),
    // which can only say true/false but can't answer whether THIS specific file should be formatted exclusively by the server.
    // Erroneous `true` answer is very dangerous as it disables the IDE's internal formatter.
    return hasDynamicCapabilityToHandleThisFile(file, capabilityAndOptionsClass)
  }

  internal fun getSignatureHelpTriggerCharacters(file: VirtualFile): List<String>? {
    val staticTriggerCharacters = serverCapabilities?.signatureHelpProvider?.triggerCharacters
    val dynamicOptions = getDynamicCapabilityOptionsForFile(file, LspDynamicCapabilities.signatureHelp)
    val dynamicTriggerCharacters = dynamicOptions?.triggerCharacters
    return when {
      dynamicTriggerCharacters != null -> dynamicTriggerCharacters
      staticTriggerCharacters != null -> staticTriggerCharacters
      else -> null
    }
  }

  internal fun getOnTypeFormattingTriggerCharacters(file: VirtualFile): List<String>? {
    val staticOptions = serverCapabilities?.documentOnTypeFormattingProvider
    val staticTriggerChars = staticOptions?.let {
      buildList {
        add(it.firstTriggerCharacter)
        it.moreTriggerCharacter?.let { chars -> addAll(chars) }
      }
    }
    val dynamicOptions = getDynamicCapabilityOptionsForFile(file, LspDynamicCapabilities.onTypeFormatting)
    val dynamicTriggerChars = dynamicOptions?.let {
      buildList {
        add(it.firstTriggerCharacter)
        it.moreTriggerCharacter?.let { chars -> addAll(chars) }
      }
    }
    return dynamicTriggerChars ?: staticTriggerChars
  }

  private fun <T : TextDocumentRegistrationOptions> hasDynamicCapabilityToHandleThisFile(
    file: VirtualFile,
    capabilityAndOptionsClass: Pair<String, Class<T>>,
  ): Boolean = getDynamicCapabilityOptionsForFile(file, capabilityAndOptionsClass) != null

  private fun <T : TextDocumentRegistrationOptions> getDynamicCapabilityOptionsForFile(
    file: VirtualFile,
    capabilityAndOptionsClass: Pair<String, Class<T>>,
  ): T? {
    if (file.isDirectory) {
      logWarn("Directory not expected here. Capability: ${capabilityAndOptionsClass.first}, file: ${file.path}")
      return null
    }

    for (options in dynamicCapabilities.getCapabilityRegistrationOptions(capabilityAndOptionsClass)) {
      val documentSelector = options.documentSelector ?: return options

      for (filter in documentSelector) {
        if (filter.scheme != null && filter.scheme != "file") continue

        val language = filter.language
        val pattern = filter.pattern
        if (language == null && pattern == null) continue
        if (language != null && language != descriptor.getLanguageId(file)) continue

        if (pattern != null) {
          if (!globMatcher.pathMatches(file.path, false, pattern, null)) {
            continue
          }
        }

        return options
      }
    }

    return null
  }

  internal fun appendServerErrorOutput(text: String) {
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
