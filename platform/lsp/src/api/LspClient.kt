// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * IntelliJ's model of a started LSP server. In [LSP terminology](https://microsoft.github.io/language-server-protocol/) the IDE is the
 * client, which is why this IDE-side model is named [LspClient] rather than after the server.
 *
 * To get an instance of [LspClient] use [LspClientManager.getClients]
 */
interface LspClient {
  val providerClass: Class<out LspIntegrationProvider>
  val project: Project

  /**
   * An [LspClientDescriptor] that is used to start and control the behavior of this [LspClient].
   * The returned object is exactly the one that the plugin passed to [LspIntegrationProvider.LspClientStarter.ensureClientStarted].
   */
  val descriptor: LspClientDescriptor

  val state: LspServerState

  val initializeResult: InitializeResult?

  /**
   * Sends a [notification](https://microsoft.github.io/language-server-protocol/specification/#notificationMessage)
   * from the IDE to the LSP server.
   *
   * The client does not send the notification when the server is not in the [LspServerState.Running] state.
   *
   * Example:
   *
   *    lspClient.sendNotification { it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(...)) }
   *
   * Custom (undocumented) notification example:
   *
   *    lspClient.sendNotification { (it as FooLsp4jServer).customNotification(...) }
   *
   * @see LspClientDescriptor.lsp4jServerClass
   */
  fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit)

  /**
   * Drops everything the IDE has cached from this server — pulled diagnostics, inlay hints, semantic tokens,
   * hovers, symbols, … — and re-requests it for the opened files.
   *
   * Call it after changing what the server bases its answers on, a settings push for example: every pulled result is
   * cached until the file changes, so without this the server's new answers are only visible after an edit. Send the
   * push first: the re-requests are ordered after everything already sent to the server.
   *
   * What is on screen stays there until the fresh results arrive, so there is no flicker. Note that this cannot
   * guarantee the server has finished applying the change before it answers; a server that re-reads its
   * configuration asynchronously should also send the matching `workspace/.../refresh` request once it has.
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  fun invalidateServerResults()

  /**
   * Sends a request to the LSP server.
   *
   * This function will return `null` if the LSP server:
   *  - is not yet initialized or is already shut down
   *  - returns a `null` response
   *
   * This function has no timeout. To limit the wait, call it inside [kotlinx.coroutines.withTimeoutOrNull].
   *
   * Example:
   *
   *    val result = lspClient.sendRequest { it.workspaceService.executeCommand(ExecuteCommandParams(...)) }
   *
   * Custom (undocumented) request example:
   *
   *    val result = lspClient.sendRequest { (it as FooLsp4jServer).customRequest(...) }
   *
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException if the LSP server responds with an error.
   *         The error does not appear in the IDE logs.
   * @see LspClientDescriptor.lsp4jServerClass
   */
  suspend fun <Lsp4jResponse> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>): Lsp4jResponse?

  /**
   * Sends a request to the LSP server and reports the
   * [partial results](https://microsoft.github.io/language-server-protocol/specification/#partialResults) of that request
   * to [partialResultConsumer].
   *
   * This [LspClient] makes a token that no other of its requests uses and gives it to [lsp4jSender]. The sender must put the token
   * into the params that it builds. A standard params class that implements [org.eclipse.lsp4j.PartialResultParams] takes it in
   * `partialResultToken`.
   *
   * Example:
   *
   *    val locations = lspClient.sendRequestWithPartialResults(::addLocationsFrom) { server, partialResultToken ->
   *      val params = ReferenceParams(documentId, position, ReferenceContext(true))
   *      params.partialResultToken = partialResultToken
   *      server.textDocumentService.references(params)
   *    }
   *
   * Every `$/progress` notification that carries the token goes to [partialResultConsumer]. The client forgets the token when the
   * request completes, when it fails, and when the calling coroutine is cancelled. A notification that arrives after that goes to
   * the work-done progress handling, as a notification of an unknown token does.
   *
   * [partialResultConsumer] gets the notification as it arrived. The payload is in `value.right`. The `lsp4j` library reads it as a
   * `com.google.gson.JsonElement`, because the protocol declares no type for it. A caller that also sends the token as the
   * `workDoneToken` gets the [org.eclipse.lsp4j.WorkDoneProgressNotification] values in `value.left`, and the IDE then shows no
   * progress indicator for them.
   *
   * [partialResultConsumer] runs on the thread that reads the messages of the server, in the order that the server sent them.
   * It must not block, and it must not wait for the result of this function. An exception of the consumer goes to the IDE log
   * and does not stop the server.
   *
   * In every other way this function behaves like [sendRequest]: it has no timeout, it returns `null` when the server is not
   * running or answers `null`, and a cancellation of the calling coroutine sends `$/cancelRequest`.
   *
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException if the LSP server responds with an error.
   *         The error does not appear in the IDE logs.
   * @see sendRequest
   * @see LspClientDescriptor.lsp4jServerClass
   */
  @ApiStatus.Experimental
  suspend fun <Lsp4jResponse> sendRequestWithPartialResults(
    partialResultConsumer: (ProgressParams) -> Unit,
    lsp4jSender: (Lsp4jServer, partialResultToken: Either<String, Int>) -> CompletableFuture<Lsp4jResponse>,
  ): Lsp4jResponse? =
    // The default sends the request and delivers no partial results: the token reaches no router, so every
    // `$/progress` of the request goes to the work-done handling. It keeps an implementation of this interface
    // outside `lsp-impl` compiling and behaviorally unchanged; the real client overrides it.
    sendRequest { server -> lsp4jSender(server, Either.forLeft("ij-partial-results-unrouted")) }

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
   *    val result = lspClient.sendRequestSync { it.workspaceService.executeCommand(ExecuteCommandParams(...)) }
   *
   * Custom (undocumented) request example:
   *
   *    val result = lspClient.sendRequestSync { (it as FooLsp4jServer).customRequest(...) }
   *
   * @see LspClientDescriptor.lsp4jServerClass
   */
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  fun <Lsp4jResponse> sendRequestSync(
    timeoutMs: Int = DEFAULT_REQUEST_TIMEOUT_MS,
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
  ): Lsp4jResponse?

  /**
   * Finds the navigation target for [targetUri], the way Go to Declaration does.
   *
   * When the server declares the URI scheme in its `workspace.textDocumentContent` capability, the target is an in-memory
   * file with server-provided content, and the function may send a `workspace/textDocumentContent` request and wait for
   * the answer. Repeated calls with one URI return the same file instance, so an existing editor tab is reused.
   * For any other URI the target is the file that [LspClientDescriptor.findFileByUri] resolves.
   */
  @ApiStatus.Internal
  @RequiresBackgroundThread
  fun findTargetFile(targetUri: String): VirtualFile? = descriptor.findFileByUri(targetUri)

  /**
   * Creates [TextDocumentIdentifier](https://microsoft.github.io/language-server-protocol/specification/#textDocumentIdentifier)
   * for the given [file] to be used in various LSP requests.
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

  /**
   * Advances and returns the version to declare for [document]'s next `didOpen`/`didChange`. This client
   * owns the number outright, so callers that must declare a version before an edit lands -- an incremental
   * change's line numbers have to be computed from the pre-edit document, for instance -- can call this
   * safely instead of trying to predict [document]'s own modification-tracking state.
   */
  fun nextDocumentVersion(document: Document): Int

  companion object {
    const val DEFAULT_REQUEST_TIMEOUT_MS: Int = 10_000
  }
}