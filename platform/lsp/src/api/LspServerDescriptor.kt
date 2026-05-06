// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServerDescriptor.Companion.LOG
import com.intellij.platform.lsp.api.customization.DeprecatedLspCustomization
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCommandsSupport
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspDocumentColorSupport
import com.intellij.platform.lsp.api.customization.LspDocumentLinkSupport
import com.intellij.platform.lsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspHoverSupport
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.lsp.api.customization.defaultLspCustomization
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.io.URLUtil
import org.eclipse.lsp4j.CallHierarchyCapabilities
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities
import org.eclipse.lsp4j.CodeLensCapabilities
import org.eclipse.lsp4j.ColorProviderCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemKindCapabilities
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.CompletionItemTagSupportCapabilities
import org.eclipse.lsp4j.CompletionListCapabilities
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DefinitionCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.DiagnosticsTagSupport
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities
import org.eclipse.lsp4j.DocumentHighlightCapabilities
import org.eclipse.lsp4j.DocumentLinkCapabilities
import org.eclipse.lsp4j.DocumentSymbolCapabilities
import org.eclipse.lsp4j.ExecuteCommandCapabilities
import org.eclipse.lsp4j.FailureHandlingKind
import org.eclipse.lsp4j.FoldingRangeCapabilities
import org.eclipse.lsp4j.FoldingRangeKind
import org.eclipse.lsp4j.FoldingRangeKindSupportCapabilities
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities
import org.eclipse.lsp4j.FormattingCapabilities
import org.eclipse.lsp4j.GeneralClientCapabilities
import org.eclipse.lsp4j.HoverCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InlayHintCapabilities
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.OnTypeFormattingCapabilities
import org.eclipse.lsp4j.ParameterInformationCapabilities
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.RangeFormattingCapabilities
import org.eclipse.lsp4j.ReferencesCapabilities
import org.eclipse.lsp4j.RenameCapabilities
import org.eclipse.lsp4j.ResourceOperationKind
import org.eclipse.lsp4j.SelectionRangeCapabilities
import org.eclipse.lsp4j.SemanticTokensCapabilities
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequestsFull
import org.eclipse.lsp4j.SemanticTokensWorkspaceCapabilities
import org.eclipse.lsp4j.ShowDocumentCapabilities
import org.eclipse.lsp4j.SignatureHelpCapabilities
import org.eclipse.lsp4j.SignatureInformationCapabilities
import org.eclipse.lsp4j.StaleRequestCapabilities
import org.eclipse.lsp4j.SymbolCapabilities
import org.eclipse.lsp4j.SymbolKindCapabilities
import org.eclipse.lsp4j.SymbolTag
import org.eclipse.lsp4j.SymbolTagSupportCapabilities
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TokenFormat
import org.eclipse.lsp4j.TypeDefinitionCapabilities
import org.eclipse.lsp4j.TypeHierarchyCapabilities
import org.eclipse.lsp4j.WindowClientCapabilities
import org.eclipse.lsp4j.WindowShowMessageRequestCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URISyntaxException

/**
 * Defines how to start the LSP server process ([startServerProcess], [createCommandLine], and [lspCommunicationChannel]),
 * and how to communicate with the running LSP server (all other functions and properties).
 *
 * See [LspServerSupportProvider] documentation for details about starting an LSP server.
 *
 * Plugins that want to run a single LSP server for the whole project, regardless of the project structure, should extend
 * [ProjectWideLspServerDescriptor].
 *
 * Normally, `LspServerDescriptor` implementations don't store any modifiable state.
 *
 * As a rule, plugins don't keep references to the `LspServerDescriptor` implementations. To get an `LspServerDescriptor` that is used to
 * start a specific LSP server, use [LspServer.descriptor], where the [LspServer] itself could be found using
 * [LspServerManager.getServersForProvider].
 *
 * To see all [window/logMessage](https://microsoft.github.io/language-server-protocol/specification/#window_logMessage)
 * and [$/logTrace](https://microsoft.github.io/language-server-protocol/specification/#traceValue) notifications from the server in the
 * `Notifications` tool window, select the `'Show in tool window'` check box for the `'LSP log: info, trace'` category
 * in Settings -> Appearance & Behavior -> Notifications.
 *
 * @param presentableName this string may appear in UI in some cases, for example:
 * - `Language Services` status bar widget item
 * ([LspServerWidgetItem.getWidgetActionText][com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem.widgetActionText])
 * - `Show Usages` popup header
 * - LSP server-related information in the `Notifications` tool window
 */
abstract class LspServerDescriptor protected constructor(
  val project: Project,
  @param:NlsSafe val presentableName: String,
  vararg val roots: VirtualFile,
) {

  /**
   * Helps to read logs. By the way, implementations can use [LOG] property defined in this class if they want.
   */
  override fun toString(): @NlsSafe String = javaClass.simpleName + "@" + project.name

  /**
   * Implementations should return `true` if the LSP server needs to track the file contents while the file is being edited.
   * In other words, `true` means that the server needs to know the maybe-not-yet-saved state of the file at any moment of time.
   * In this case the IDE will take care of sending the `didOpen`, `didChange`, and `didClose` notifications to the server
   * according to the
   * [specification](https://microsoft.github.io/language-server-protocol/specification/#textDocument_synchronization).
   *
   * If implementation returns `false` then the server will only know the contents of the file as it is stored on the disk.
   *
   * The implementation must be idempotent, have no side effects,
   * and the result must depend only on the given file but not on the project settings or whatever else.
   * The call site has the right to cache the returned result.
   *
   * Typical implementation:
   *
   * ```kotlin
   *   override fun isSupportedFile(file: VirtualFile) = file.extension == "foo"
   * ```
   *
   * or
   *
   * ```kotlin
   *   override fun isSupportedFile(file: VirtualFile) = file.fileType == FooFileType.INSTANCE
   * ```
   *
   * @param file the file is guaranteed to be valid, in a local file system, and within project content roots. However, it might be not
   *             within the roots configured for this LSP server, which is usually fine.
   */
  @RequiresReadLock
  abstract fun isSupportedFile(file: VirtualFile): Boolean

  /**
   * Starts the LSP server process.
   * Usually, plugins don't need to override this function, but only implement the [createCommandLine] function.
   */
  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  open fun startServerProcess(): OSProcessHandler {
    // LSP spec says: "It defaults to utf-8, which is the only encoding supported right now"
    // see https://microsoft.github.io/language-server-protocol/specification/#contentPart
    val startingCommandLine = createCommandLine().withCharset(Charsets.UTF_8)
    LOG.info("$this: starting LSP server: $startingCommandLine")
    return object : OSProcessHandler(startingCommandLine) {
      override fun readerOptions(): BaseOutputReader.Options = object : BaseOutputReader.Options() {
        override fun policy(): BaseDataReader.SleepingPolicy = forMostlySilentProcess().policy()

        // Must not loose '\r' in "\r\n" line endings. They affect char count, which must match `Content-Length`
        override fun splitToLines(): Boolean = false
      }
    }
  }

  /**
   * The command line to start the server.
   * Each plugin must implement this function, unless:
   * - the plugin implements the more generic function [startServerProcess]
   * - the plugin uses [LspCommunicationChannel.Socket] with `startProcess` set to `false`
   */
  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  open fun createCommandLine(): GeneralCommandLine {
    // this text goes only to the IDE logs
    @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
    throw ExecutionException("createCommandLine() function not implemented")
  }

  /**
   * The channel for the communication between the IDE and the LSP server: StdIO or Socket
   */
  open val lspCommunicationChannel: LspCommunicationChannel = LspCommunicationChannel.StdIO

  /**
   * Returns a [DocumentUri](https://microsoft.github.io/language-server-protocol/specification/#documentUri), which can be used in various
   * requests to the LSP server.
   * The default implementation simply calls [getFilePath] and converts it to `file://...` URI.
   */
  open fun getFileUri(file: VirtualFile): String {
    val escapedPath = URLUtil.encodePath(getFilePath(file))
    val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, escapedPath)
    val uri = VfsUtil.toUri(url)?.toString() ?: url
    return lowercaseWindowsDriveAndEscapeColon(uri)
  }

  /**
   * The LSP spec [requires](https://microsoft.github.io/language-server-protocol/specification/#uri)
   * that all servers work fine with URIs in both formats: `file:///C:/foo` and `file:///c%3A/foo`.
   *
   * VS Code always sends a lowercased Windows drive letter and always escapes colon
   * (see this [issue](https://github.com/microsoft/vscode-languageserver-node/issues/1280)
   * and the related [pull request](https://github.com/microsoft/language-server-protocol/pull/1786)).
   *
   * Some LSP servers support only the VS Code-friendly URI format (`file:///c%3A/foo`), so it's safer to use it by default.
   */
  private fun lowercaseWindowsDriveAndEscapeColon(uri: String): String {
    val prefix = "file:///"
    if (uri.startsWith(prefix) && OSAgnosticPathUtil.startsWithWindowsDrive(uri.substring(prefix.length))) {
      return prefix + uri[prefix.length].lowercase() + "%3A" + uri.substring(prefix.length + 2)
    }
    return uri
  }

  /**
   * @see getFileUri
   */
  protected open fun getFilePath(file: VirtualFile): String = file.path

  /**
   * Extracts a file path from [fileUri] and calls [findLocalFileByPath]. Respects only `file://...` URIs.
   * @param fileUri a [DocumentUri](https://microsoft.github.io/language-server-protocol/specification/#documentUri) received from the LSP
   *                server within some response or notification
   */
  open fun findFileByUri(fileUri: String): VirtualFile? {
    val badWslUriStart = "file:///wsl$/"
    val fixedFileUri = when {
      fileUri.startsWith(badWslUriStart) -> "file:////wsl$/${fileUri.substring(badWslUriStart.length)}"
      else -> fileUri
    }

    return try {
      val uri = URI(fixedFileUri)
      if (URLUtil.FILE_PROTOCOL != uri.scheme) {
        LOG.warn("Unexpected URI scheme: $fileUri")
        return null
      }
      val path = uri.path
      if (path == null) {
        LOG.warn("Unexpected URI (no path): $fileUri")
        return null
      }
      findLocalFileByPath(path)
    }
    catch (e: URISyntaxException) {
      LOG.warn("Malformed URI: " + fileUri + "; " + e.message)
      null
    }
  }

  /**
   * @see findFileByUri
   */
  protected open fun findLocalFileByPath(path: String): VirtualFile? = LocalFileSystem.getInstance().findFileByPath(path)

  /**
   * Returns a `languageId` field of the [TextDocumentItem](https://microsoft.github.io/language-server-protocol/specification#textDocumentItem) class,
   * which is needed for the [textDocument/didOpen](https://microsoft.github.io/language-server-protocol/specification#textDocumentItem)
   * notification. `languageId` is usually equal to the lowercased file extension. Standard implementation also handles some known
   * exceptions to this rule.
   */
  open fun getLanguageId(file: VirtualFile): String = Companion.getLanguageId(file)

  /**
   * [InitializeParams](https://microsoft.github.io/language-server-protocol/specification#initializeParams) object is sent to the LSP
   * server as [initialize](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#initialize) request.
   */
  open fun createInitializeParams(): InitializeParams = InitializeParams().apply {
    clientInfo = ClientInfo(ApplicationNamesInfo.getInstance().fullProductNameWithEdition,
                            ApplicationInfo.getInstance().build.asStringWithoutProductCode())

    capabilities = clientCapabilities

    if (roots.size == 1) {
      // Some old servers might need this old way of setting roots
      @Suppress("DEPRECATION")
      rootUri = getFileUri(roots[0])
      @Suppress("DEPRECATION")
      rootPath = getFilePath(roots[0])
    }

    workspaceFolders = roots.map { root: VirtualFile -> WorkspaceFolder(getFileUri(root), root.name) }

    createInitializationOptions()?.let { initializationOptions = it }
  }

  /**
   * [ClientCapabilities](https://microsoft.github.io/language-server-protocol/specification/#clientCapabilities) is a mandatory field in
   * [InitializeParams](https://microsoft.github.io/language-server-protocol/specification#initializeParams) class, which is sent to the LSP
   * server as [initialize](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#initialize) request.
   *
   * Plugins may override this property and tune capabilities according to their needs.
   */
  open val clientCapabilities: ClientCapabilities
    get() = ClientCapabilities().apply {
      workspace = WorkspaceClientCapabilities().apply {
        configuration = true
        applyEdit = true
        workspaceFolders = true
        //configuration = true // keep false by default because [getWorkspaceConfiguration] returns null by default
        workspaceEdit = WorkspaceEditCapabilities().apply {
          documentChanges = true
          resourceOperations = listOf(ResourceOperationKind.Create)
          failureHandling = FailureHandlingKind.Abort
          normalizesLineEndings = true
        }
        didChangeWatchedFiles = DidChangeWatchedFilesCapabilities(true).apply {
          relativePatternSupport = true
        }
        executeCommand = ExecuteCommandCapabilities(false)
        semanticTokens = SemanticTokensWorkspaceCapabilities().apply {
          refreshSupport = true
        }
        inlayHint = InlayHintWorkspaceCapabilities().apply {
          refreshSupport = true
        }
        symbol = SymbolCapabilities(true).apply {
          symbolKind = SymbolKindCapabilities().apply {
            valueSet = lspCustomization.symbolKindCustomizer.supportedKinds
          }
        }
      }

      textDocument = TextDocumentClientCapabilities().apply {
        synchronization = SynchronizationCapabilities().apply {
          // `dynamicRegistration = false` because the IDE doesn't check dynamically registered capabilities before sending
          // `didOpen` / `didChange` / `didClose` notifications.
          // It means that the IDE relies on static capability `LspServer.initializeResult.capabilities.textDocumentSync`
          dynamicRegistration = false
          willSave = false
          willSaveWaitUntil = false
          didSave = true
        }
        inlayHint = InlayHintCapabilities(true)
        documentHighlight = DocumentHighlightCapabilities(true)
        diagnostic = DiagnosticCapabilities(true)
        definition = DefinitionCapabilities().apply {
          linkSupport = true
        }
        typeDefinition = TypeDefinitionCapabilities().apply {
          linkSupport = true
        }
        completion = CompletionCapabilities().apply {
          completionItem = CompletionItemCapabilities().apply {
            documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
            deprecatedSupport = true
            tagSupport = CompletionItemTagSupportCapabilities(listOf(CompletionItemTag.Deprecated))
            insertReplaceSupport = true
            labelDetailsSupport = true
            snippetSupport = true
            resolveSupport = CompletionItemResolveSupportCapabilities().apply {
              properties = listOf("documentation")
            }
          }
          completionItemKind = CompletionItemKindCapabilities().apply {
            valueSet = CompletionItemKind.entries
          }
          completionList = CompletionListCapabilities().apply {
            itemDefaults = listOf("commitCharacters", "editRange", "insertTextFormat", "insertTextMode", "data")
          }
        }
        hover = HoverCapabilities().apply {
          contentFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
        }
        signatureHelp = SignatureHelpCapabilities(true).apply {
          signatureInformation = SignatureInformationCapabilities().apply {
            documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
            parameterInformation = ParameterInformationCapabilities().apply {
              labelOffsetSupport = true
            }
            activeParameterSupport = true
          }
        }
        documentSymbol = DocumentSymbolCapabilities().apply {
          dynamicRegistration = true
          symbolKind = SymbolKindCapabilities().apply {
            valueSet = lspCustomization.symbolKindCustomizer.supportedKinds
          }
          hierarchicalDocumentSymbolSupport = true
          tagSupport = SymbolTagSupportCapabilities(listOf(SymbolTag.Deprecated))
          labelSupport = false
        }
        foldingRange = FoldingRangeCapabilities().apply {
          dynamicRegistration = true
          rangeLimit = null
          lineFoldingOnly = false
          foldingRangeKind = FoldingRangeKindSupportCapabilities().apply {
            valueSet = listOf(FoldingRangeKind.Imports, FoldingRangeKind.Region)
          }
          foldingRange = FoldingRangeSupportCapabilities().apply {
            collapsedText = true
          }
        }

        val semanticTokensSupport = lspCustomization.semanticTokensCustomizer
        if (semanticTokensSupport is LspSemanticTokensSupport) {
          semanticTokens = SemanticTokensCapabilities().apply {
            requests = SemanticTokensClientCapabilitiesRequests().apply {
              range = Either.forLeft(false)
              full = Either.forRight(SemanticTokensClientCapabilitiesRequestsFull().apply {
                delta = false
              })
              tokenTypes = semanticTokensSupport.tokenTypes
              tokenModifiers = semanticTokensSupport.tokenModifiers
              formats = listOf(TokenFormat.Relative)
              overlappingTokenSupport = true
              multilineTokenSupport = true
              serverCancelSupport = false
            }
          }
        }

        publishDiagnostics = PublishDiagnosticsCapabilities().apply {
          versionSupport = true
          tagSupport = Either.forRight(DiagnosticsTagSupport(listOf(DiagnosticTag.Unnecessary, DiagnosticTag.Deprecated)))
          dataSupport = true
        }
        codeAction = CodeActionCapabilities().apply {
          codeActionLiteralSupport = CodeActionLiteralSupportCapabilities().apply {
            codeActionKind = CodeActionKindCapabilities(listOf(
              CodeActionKind.QuickFix,
              CodeActionKind.Empty,
              CodeActionKind.Source,
              CodeActionKind.Refactor,
            ))
          }
          disabledSupport = true
          dataSupport = true
          resolveSupport = CodeActionResolveSupportCapabilities().apply {
            properties = listOf("edit")
          }
        }
        formatting = FormattingCapabilities(true)
        rangeFormatting = RangeFormattingCapabilities(true)
        onTypeFormatting = OnTypeFormattingCapabilities(true)
        references = ReferencesCapabilities(true)
        colorProvider = ColorProviderCapabilities(true)
        documentLink = DocumentLinkCapabilities(true).apply {
          tooltipSupport = true
        }
        callHierarchy = CallHierarchyCapabilities(true)
        typeHierarchy = TypeHierarchyCapabilities(true)
        selectionRange = SelectionRangeCapabilities(true)
        codeLens = CodeLensCapabilities(true)
        rename = RenameCapabilities(true).apply {
          prepareSupport = true
        }
      }

      notebookDocument = null

      window = WindowClientCapabilities().apply {
        showMessage = WindowShowMessageRequestCapabilities()
        showDocument = ShowDocumentCapabilities(true)
        workDoneProgress = true
      }

      general = GeneralClientCapabilities().apply {
        staleRequestSupport = StaleRequestCapabilities().apply {
          isCancel = true
        }
      }

      experimental = null
    }

  /**
   * `initializationOptions` is an optional field in the
   * [InitializeParams](https://microsoft.github.io/language-server-protocol/specification#initializeParams)
   * class, which is sent to the LSP server as
   * [initialize](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#initialize) request.
   */
  open fun createInitializationOptions(): Any? = null

  /**
   * Creates an implementation of the [org.eclipse.lsp4j.services.LanguageClient] interface.
   * It handles all standard requests and notifications that the LSP server sends to the IDE.
   * 'Standard' requests and notifications are the ones that are documented
   * in the official [LSP specification](https://microsoft.github.io/language-server-protocol/specification).
   *
   * To handle custom undocumented requests/notifications from the server, plugins may override this function
   * and return their subclass of the [Lsp4jClient] class.
   * See [Lsp4jClient] class documentation for more information.
   */
  open fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient = Lsp4jClient(handler)

  /**
   * Returns a class that should be used as a [Lsp4jServer] for this [LspServer].
   *
   * A plugin needs to define a custom sub-interface of the [Lsp4jServer] interface only if it needs to send
   * some custom undocumented notifications or requests to the LSP server.
   *
   * Note that there's no need to implement such a custom sub-interface.
   * The `lsp4j` library will generate the interface implementation using Java reflection ([Proxy][java.lang.reflect.Proxy])
   * based on `lsp4j`-specific annotations.
   *
   * Example:
   *
   * ```kotlin
   * interface FooLsp4jServer : Lsp4jServer {
   *   @JsonRequest("foo/customRequest")
   *   fun customRequest(params: CustomParams): CompletableFuture<CustomResult>
   *
   *   @JsonNotification("foo/customNotification")
   *   fun customNotification(params: CustomNotificationParams)
   * }
   * ```
   *
   * @see [LspServer.sendNotification]
   * @see [LspServer.sendRequest]
   * @see [LspServer.sendRequestSync]
   */
  open val lsp4jServerClass: Class<out Lsp4jServer> = Lsp4jServer::class.java

  /**
   * Plugins may provide their listeners to get notified about [LspServer] events.
   */
  open val lspServerListener: LspServerListener? = null

  /**
   * Plugins may override the [LspCustomization] class and its properties to customize how different LSP features
   * are handled in the IDE, or disable them completely.
   */
  open val lspCustomization: LspCustomization = DeprecatedLspCustomization(this)

  //<editor-fold desc="Deprecated stuff.">

  /**
   * @see LspCustomization.goToDefinitionCustomizer
   */
  @Deprecated("Use lspCustomization.goToDefinitionCustomizer instead",
              ReplaceWith("lspCustomization.goToDefinitionCustomizer is LspGoToDefinitionSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspGoToDefinitionSupport: Boolean = defaultLspCustomization.goToDefinitionCustomizer is LspGoToDefinitionSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.goToDefinitionCustomizer is LspGoToDefinitionSupport else field

  /**
   * @see LspCustomization.goToTypeDefinitionCustomizer
   */
  @Deprecated("Use lspCustomization.goToTypeDefinitionCustomizer instead",
              ReplaceWith("lspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspGoToTypeDefinitionSupport: Boolean = defaultLspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionSupport else field

  /**
   * @see LspCustomization.hoverCustomizer
   */
  @Deprecated("Use lspCustomization.hoverCustomizer instead", ReplaceWith("lspCustomization.hoverCustomizer is LspHoverSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspHoverSupport: Boolean = defaultLspCustomization.hoverCustomizer is LspHoverSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.hoverCustomizer is LspHoverSupport else field

  /**
   * @see LspCustomization.completionCustomizer
   */
  @Deprecated("Use lspCustomization.completionCustomizer instead",
              ReplaceWith("lspCustomization.completionCustomizer as? LspCompletionSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspCompletionSupport: LspCompletionSupport? = defaultLspCustomization.completionCustomizer as? LspCompletionSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.completionCustomizer as? LspCompletionSupport else field

  /**
   * @see LspCustomization.semanticTokensCustomizer
   */
  @Deprecated("Use lspCustomization.semanticTokensCustomizer instead",
              ReplaceWith("lspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspSemanticTokensSupport: LspSemanticTokensSupport? =
    defaultLspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport else field

  /**
   * @see LspCustomization.diagnosticsCustomizer
   */
  @Deprecated("Use lspCustomization.diagnosticsCustomizer instead",
              ReplaceWith("lspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspDiagnosticsSupport: LspDiagnosticsSupport? = defaultLspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport else field

  /**
   * @see LspCustomization.codeActionsCustomizer
   */
  @Deprecated("Use lspCustomization.codeActionsCustomizer instead",
              ReplaceWith("lspCustomization.codeActionsCustomizer as? LspCodeActionsSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspCodeActionsSupport: LspCodeActionsSupport? = defaultLspCustomization.codeActionsCustomizer as? LspCodeActionsSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.codeActionsCustomizer as? LspCodeActionsSupport else field

  /**
   * @see LspCustomization.commandsCustomizer
   */
  @Deprecated("Use lspCustomization.commandsCustomizer instead", ReplaceWith("lspCustomization.commandsCustomizer as? LspCommandsSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspCommandsSupport: LspCommandsSupport? = defaultLspCustomization.commandsCustomizer as? LspCommandsSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.commandsCustomizer as? LspCommandsSupport else field

  /**
   * @see LspCustomization.formattingCustomizer
   */
  @Deprecated("Use lspCustomization.formattingCustomizer instead",
              ReplaceWith("lspCustomization.formattingCustomizer as? LspFormattingSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspFormattingSupport: LspFormattingSupport? = defaultLspCustomization.formattingCustomizer as? LspFormattingSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.formattingCustomizer as? LspFormattingSupport else field

  /**
   * @see LspCustomization.findReferencesCustomizer
   */
  @Deprecated("Use lspCustomization.findReferencesCustomizer instead",
              ReplaceWith("lspCustomization.findReferencesCustomizer as? LspFindReferencesSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspFindReferencesSupport: LspFindReferencesSupport? =
    defaultLspCustomization.findReferencesCustomizer as? LspFindReferencesSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.findReferencesCustomizer as? LspFindReferencesSupport else field

  /**
   * @see LspCustomization.documentColorCustomizer
   */
  @Deprecated("Use lspCustomization.documentColorCustomizer instead",
              ReplaceWith("lspCustomization.documentColorCustomizer as? LspDocumentColorSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspDocumentColorSupport: LspDocumentColorSupport? = defaultLspCustomization.documentColorCustomizer as? LspDocumentColorSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.documentColorCustomizer as? LspDocumentColorSupport else field

  /**
   * @see LspCustomization.documentLinkCustomizer
   */
  @Deprecated("Use lspCustomization.documentLinkCustomizer instead",
              ReplaceWith("lspCustomization.documentLinkCustomizer as? LspDocumentLinkSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspDocumentLinkSupport: LspDocumentLinkSupport? = defaultLspCustomization.documentLinkCustomizer as? LspDocumentLinkSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.documentLinkCustomizer as? LspDocumentLinkSupport else field

  //</editor-fold>

  /**
   * Override this function to handle
   * [workspace/configuration](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_configuration)
   * requests from the server. Implementations should check `item.section` and respond only to the requests that they understand.
   * As a rule, implementations return `super.getWorkspaceConfiguration(item)` as a fallback.
   *
   * Example:
   *
   * ```kotlin
   *   override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? {
   *     if (item.section == "foo") {
   *       @Suppress("unused")
   *       return object {
   *         val fooOptionExpectedByServer: Boolean = true
   *       }
   *     }
   *
   *     return super.getWorkspaceConfiguration(item)
   *    }
   * ```
   */
  open fun getWorkspaceConfiguration(item: ConfigurationItem): Any? = null

  companion object {
    @JvmField
    val LOG: Logger = Logger.getInstance(LspServerDescriptor::class.java)

    fun getLanguageId(file: VirtualFile): String {
      val nameLowercased = StringUtil.toLowerCase(file.name)
      FILE_NAME_ENDING_TO_LANGUAGE_ID.find { nameLowercased.endsWith(it.first) }?.let { return it.second }
      return StringUtil.toLowerCase(file.extension)?.let { FILE_EXTENSION_TO_LANGUAGE_ID[it] ?: it } ?: ""
    }

    private val FILE_NAME_ENDING_TO_LANGUAGE_ID: List<Pair<String, String>> = listOf(
      ".blade.php" to "blade"
    )

    // https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocumentItem
    // Language ID is usually equal to the lowercased file extension.
    // This map tracks only non-standard cases.
    @Suppress("SpellCheckingInspection")
    private val FILE_EXTENSION_TO_LANGUAGE_ID: Map<String, String> = mapOf(
      "js" to "javascript",
      "cjs" to "javascript",
      "mjs" to "javascript",
      "jsx" to "javascriptreact",
      "ts" to "typescript",
      "tsx" to "typescriptreact",

      "fs" to "fsharp",
      "fsx" to "fsharp",
      "handlebars" to "handlebars",
      "pcss" to "postcss",
      "phtml" to "php",
      "pug" to "jade",
      "py" to "python",
      "rb" to "ruby",
      "tpl" to "smarty",
    )
  }
}
