// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.CompletableFuture

/**
 * IntelliJ's model of a started LSP server.
 *
 * To get an instance of [LspServer] use [LspServerManager.getServersForProvider]
 */
interface LspServer {
  val providerClass: Class<out LspServerSupportProvider>
  val project: Project

  /**
   * An [LspServerDescriptor] that is used to start and control the behavior of this [LspServer].
   * The returned object is exactly the one that the plugin passed to [LspServerSupportProvider.LspServerStarter.ensureServerStarted].
   */
  val descriptor: LspServerDescriptor

  val state: LspServerState

  val initializeResult: InitializeResult?

  /**
   * Sends a [notification](https://microsoft.github.io/language-server-protocol/specification/#notificationMessage)
   * from the IDE to the LSP server.
   *
   * Example:
   *
   *    lspServer.sendNotification { it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(...)) }
   *
   * Custom (undocumented) notification example:
   *
   *    lspServer.sendNotification { (it as FooLsp4jServer).customNotification(...) }
   *
   * @see LspServerDescriptor.lsp4jServerClass
   */
  fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit)

  /**
   * Sends a request to the LSP server.
   *
   * This function will return `null` if the LSP server:
   *  - is not yet initialized or is already shut down
   *  - doesn't send any response within 10 seconds
   *  - returns a `null` response
   *  - responds with an error (the error will appear in the IDE logs)
   *
   * Example:
   *
   *    val result = lspServer.sendRequest { it.workspaceService.executeCommand(ExecuteCommandParams(...)) }
   *
   * Custom (undocumented) request example:
   *
   *    val result = lspServer.sendRequest { (it as FooLsp4jServer).customRequest(...) }
   *
   * @see LspServerDescriptor.lsp4jServerClass
   */
  suspend fun <Lsp4jResponse> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>): Lsp4jResponse?

  /**
   * Sends a request to the LSP server and waits for the response synchronously.
   *
   * Waiting is cancelable (thanks to regular [ProgressManager.checkCanceled] calls).
   * However, cancellability may not work if this function is called from a coroutine.
   * Prefer the coroutine-friendly function [sendRequest] when possible.
   *
   * This function will return `null` if the LSP server:
   *  - is not yet initialized or is already shut down
   *  - doesn't send any response within [timeoutMs] milliseconds (10 seconds by default)
   *  - returns a `null` response
   *  - responds with an error (the error will appear in the IDE logs)
   *
   * Example:
   *
   *    val result = lspServer.sendRequestSync { it.workspaceService.executeCommand(ExecuteCommandParams(...)) }
   *
   * Custom (undocumented) request example:
   *
   *    val result = lspServer.sendRequestSync { (it as FooLsp4jServer).customRequest(...) }
   *
   * @see LspServerDescriptor.lsp4jServerClass
   */
  @RequiresBackgroundThread
  fun <Lsp4jResponse> sendRequestSync(
    timeoutMs: Int = DEFAULT_REQUEST_TIMEOUT_MS,
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
  ): Lsp4jResponse?

  /**
   * Creates [TextDocumentIdentifier](https://microsoft.github.io/language-server-protocol/specification/#textDocumentIdentifier)
   * for the given [file] to be used in various LSP server requests.
   */
  fun getDocumentIdentifier(file: VirtualFile): TextDocumentIdentifier

  /**
   * Returns a text document version as specified by, for example,
   * [TextDocumentItem](https://microsoft.github.io/language-server-protocol/specification/#textDocumentItem),
   * [VersionedTextDocumentIdentifier](https://microsoft.github.io/language-server-protocol/specification/#versionedTextDocumentIdentifier),
   * [OptionalVersionedTextDocumentIdentifier](https://microsoft.github.io/language-server-protocol/specification/#optionalVersionedTextDocumentIdentifier),
   * or [PublishDiagnosticsParams](https://microsoft.github.io/language-server-protocol/specification/#publishDiagnosticsParams)
   */
  fun getDocumentVersion(document: Document): Int

  companion object {
    const val DEFAULT_REQUEST_TIMEOUT_MS: Int = 10_000
  }
}