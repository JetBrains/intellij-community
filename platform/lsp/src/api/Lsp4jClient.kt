// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the [org.eclipse.lsp4j.services.LanguageClient] interface.
 * It handles all standard requests and notifications that the LSP server sends to the IDE.
 * 'Standard' requests and notifications are the ones that are documented
 * in the official [LSP specification](https://microsoft.github.io/language-server-protocol/specification).
 *
 * To handle custom undocumented requests/notifications from the server, plugins need to override [LspClientDescriptor.createLsp4jClient]
 * and return their subclass of this [Lsp4jClient]. This subclass should contain specially annotated functions, which will be called
 * via reflection by the `lsp4j` library once the corresponding request/notification arrives from the LSP server.
 *
 * Example:
 *
 *    @JsonNotification("@/foo/bar")
 *    fun fooBar(fooBar: FooBarNotification) { ... }
 *
 * @see LspClientDescriptor.createLsp4jClient
 */
@ApiStatus.OverrideOnly
open class Lsp4jClient(private val serverNotificationsHandler: LspServerNotificationsHandler) : LanguageClient {
  final override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> =
    serverNotificationsHandler.applyEdit(params)

  final override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> =
    serverNotificationsHandler.registerCapability(params)

  final override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> =
    serverNotificationsHandler.unregisterCapability(params)

  override fun telemetryEvent(`object`: Any): Unit =
    serverNotificationsHandler.telemetryEvent(`object`)

  final override fun publishDiagnostics(params: PublishDiagnosticsParams): Unit =
    serverNotificationsHandler.publishDiagnostics(params)

  final override fun showMessage(params: MessageParams): Unit =
    serverNotificationsHandler.showMessage(params)

  final override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
    serverNotificationsHandler.showMessageRequest(params)

  final override fun showDocument(params: ShowDocumentParams): CompletableFuture<ShowDocumentResult> =
    serverNotificationsHandler.showDocument(params)

  final override fun logMessage(params: MessageParams): Unit =
    serverNotificationsHandler.logMessage(params)

  final override fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>> =
    serverNotificationsHandler.workspaceFolders()

  final override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> =
    serverNotificationsHandler.configuration(params)

  final override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> =
    serverNotificationsHandler.createProgress(params)

  final override fun notifyProgress(params: ProgressParams): Unit =
    serverNotificationsHandler.notifyProgress(params)

  final override fun logTrace(params: LogTraceParams): Unit =
    serverNotificationsHandler.logTrace(params)

  final override fun refreshSemanticTokens(): CompletableFuture<Void> =
    serverNotificationsHandler.refreshSemanticTokens()

  final override fun refreshCodeLenses(): CompletableFuture<Void> =
    serverNotificationsHandler.refreshCodeLenses()

  final override fun refreshInlayHints(): CompletableFuture<Void> =
    serverNotificationsHandler.refreshInlayHints()

  final override fun refreshInlineValues(): CompletableFuture<Void> =
    serverNotificationsHandler.refreshInlineValues()

  final override fun refreshDiagnostics(): CompletableFuture<Void> =
    serverNotificationsHandler.refreshDiagnostics()
}


/**
 * Plugins don't need to use this interface.
 *
 * Its internal implementation handles all standard (documented in the official LSP specification) requests and notifications
 * that the LSP server sends to the IDE.
 */
interface LspServerNotificationsHandler {
  fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse>
  fun registerCapability(params: RegistrationParams): CompletableFuture<Void>
  fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void>
  fun telemetryEvent(`object`: Any)
  fun publishDiagnostics(params: PublishDiagnosticsParams)
  fun showMessage(params: MessageParams)
  fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem>
  fun showDocument(params: ShowDocumentParams): CompletableFuture<ShowDocumentResult>
  fun logMessage(params: MessageParams)
  fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>>
  fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>>
  fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void>
  fun notifyProgress(params: ProgressParams)
  fun logTrace(params: LogTraceParams)
  fun refreshSemanticTokens(): CompletableFuture<Void>
  fun refreshCodeLenses(): CompletableFuture<Void>
  fun refreshInlayHints(): CompletableFuture<Void>
  fun refreshInlineValues(): CompletableFuture<Void>
  fun refreshDiagnostics(): CompletableFuture<Void>
}
