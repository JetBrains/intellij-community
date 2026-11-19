// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeServer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class ExpectedNotification(val method: String, val predicate: (Any?) -> Boolean, val deferred: CompletableDeferred<Unit>)

internal class ExpectedRequest(
  val method: String,
  val predicate: (Any?) -> Boolean,
  val handler: suspend (Any?) -> Any?,
  val deferred: CompletableDeferred<Unit>,
)

internal class ExpectationHolder {
  private val lock = Any()
  private val requests = mutableListOf<ExpectedRequest>()
  private val notifications = mutableListOf<ExpectedNotification>()

  fun addRequest(request: ExpectedRequest) {
    synchronized(lock) { requests.add(request) }
  }

  fun removeRequest(request: ExpectedRequest) {
    synchronized(lock) { requests.remove(request) }
  }

  fun addNotification(notification: ExpectedNotification) {
    synchronized(lock) { notifications.add(notification) }
  }

  fun removeNotification(notification: ExpectedNotification) {
    synchronized(lock) { notifications.remove(notification) }
  }

  fun matchRequest(method: String, parameter: Any?): ExpectedRequest? {
    synchronized(lock) {
      val match = requests.find { it.method == method && it.predicate(parameter) } ?: return null
      requests.remove(match)
      match.deferred.complete(Unit)
      return match
    }
  }

  fun matchNotification(method: String, parameter: Any?): Boolean {
    synchronized(lock) {
      val match = notifications.find { it.method == method && it.predicate(parameter) } ?: return false
      notifications.remove(match)
      match.deferred.complete(Unit)
      return true
    }
  }
}

/**
 * The fake LSP server of one connection of [FakeLspServerRelay], which is one server process of the IDE.
 *
 * It answers the lifecycle messages itself. Any other message needs a matching expectation of a [FakeLspServerSession],
 * unless its method is in [allowedUnmatchedMethods]. The server answers an unmatched request with `MethodNotFound`
 * and passes the description of each unmatched message to [onUnmatched].
 *
 * The same rules as `FakeLspServer` of `intellij.platform.lsp.impl.tests`, which is a `Process` inside the IDE under test.
 */
internal class FakeLspServer(
  private val socket: Socket,
  configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  val expectations: ExpectationHolder,
  private val allowedUnmatchedMethods: Set<String>,
  private val onUnmatched: (String) -> Unit,
  private val onHandlerFailure: (Throwable) -> Unit,
  private val handlerScope: CoroutineScope,
) {
  private val serverName = "FakeLspServer"

  private val serverCapabilities = ServerCapabilities().apply {
    textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
      openClose = true
      change = TextDocumentSyncKind.Full
    })
    configureServerCapabilities?.invoke(this)
  }

  /** The URIs of the documents that the client opened and did not close. */
  private val openedUris: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** The params of `initialize`, once the client sent it. */
  @Volatile
  var initializeParams: InitializeParams? = null
    private set

  @Volatile
  var initialized: Boolean = false
    private set

  // lsp4j reads the messages of the client on this executor. It runs on the IO dispatcher of the coroutines, and not on a thread
  // of its own: the test framework reports any other thread that outlives a test.
  private val executor = DispatcherExecutorService(Dispatchers.IO.asExecutor())

  private val launcher = LSPLauncher.Builder<LanguageClient>()
    .setLocalService(ServiceEndpoints.toServiceObject(FakeEndpoint(), LanguageServer::class.java))
    .setRemoteInterface(LanguageClient::class.java)
    .setInput(socket.getInputStream())
    .setOutput(socket.getOutputStream())
    .validateMessages(true)
    .setExecutorService(executor)
    .create()

  private lateinit var listening: Future<Void>

  /** Starts reading the messages of the client. Separate from the constructor, so the caller can publish the server first. */
  fun start() {
    listening = launcher.startListening()
  }

  val remoteLanguageClient: LanguageClient get() = launcher.remoteProxy

  /** The URI that the client uses for the file with [relativePath] in the project, or `null` when the server can't tell yet. */
  fun fileUri(relativePath: String): String? {
    openedUris.firstOrNull { it.endsWith("/$relativePath") }?.let { return it }
    val params = initializeParams ?: return null
    @Suppress("DEPRECATION")
    val rootUri = params.workspaceFolders?.firstOrNull()?.uri ?: params.rootUri ?: return null
    return rootUri.trimEnd('/') + "/" + relativePath
  }

  fun hasOpened(relativePath: String): Boolean = openedUris.any { it.endsWith("/$relativePath") }

  /** Closes the connection, so the relay process exits without the LSP shutdown, as a crashed server does. */
  fun terminate() {
    listening.cancel(true)
    runCatching { socket.close() }
  }

  private inner class FakeEndpoint : Endpoint {
    override fun request(method: String, parameter: Any?): CompletableFuture<*> {
      if (method == "initialize") initializeParams = parameter as? InitializeParams

      val waiter = expectations.matchRequest(method, parameter)
      if (waiter != null) {
        // The handler starts on the message thread, so one that doesn't suspend answers before the next message is read.
        // lsp4j cancels the returned future on $/cancelRequest, which cancels the handler.
        return handlerScope.future(start = CoroutineStart.UNDISPATCHED) {
          try {
            waiter.handler(parameter)
          }
          catch (e: Throwable) {
            // A ResponseErrorException is a deliberate error response. Anything else means the handler of the test is broken.
            if (e !is CancellationException && e !is ResponseErrorException) onHandlerFailure(e)
            throw e
          }
        }
      }

      val result = when (method) {
        "initialize" -> InitializeResult(serverCapabilities, ServerInfo(serverName, "0.0.1"))
        "shutdown" -> null
        else -> {
          if (method !in allowedUnmatchedMethods) {
            reportUnmatched("request", method, parameter)
            return CompletableFuture.failedFuture<Any?>(
              ResponseErrorException(ResponseError(ResponseErrorCode.MethodNotFound, "Unmatched LSP request: $method", null)))
          }
          null
        }
      }
      return CompletableFuture.completedFuture(result)
    }

    override fun notify(method: String, parameter: Any?) {
      when (method) {
        "initialized" -> initialized = true
        "textDocument/didOpen" -> openedUris.add((parameter as DidOpenTextDocumentParams).textDocument.uri)
        "textDocument/didClose" -> openedUris.remove((parameter as DidCloseTextDocumentParams).textDocument.uri)
        "exit" -> listening.cancel(true)
      }

      val matched = expectations.matchNotification(method, parameter)
      if (!matched && method !in LIFECYCLE_NOTIFICATIONS && method !in allowedUnmatchedMethods) {
        reportUnmatched("notification", method, parameter)
      }
    }

    private fun reportUnmatched(kind: String, method: String, parameter: Any?) {
      onUnmatched("Unmatched LSP $kind $method: ${parameter.toString().replace(WHITESPACE, " ").take(500)}")
    }
  }
}

/** Just enough of an [java.util.concurrent.ExecutorService] for lsp4j, which only submits its message reading loop. */
private class DispatcherExecutorService(private val executor: Executor) : AbstractExecutorService() {
  override fun execute(command: Runnable) = executor.execute(command)
  override fun shutdown() = Unit
  override fun shutdownNow(): List<Runnable> = emptyList()
  override fun isShutdown(): Boolean = false
  override fun isTerminated(): Boolean = false
  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}

private val LIFECYCLE_NOTIFICATIONS = setOf("initialized", "exit")

private val WHITESPACE = Regex("\\s+")

/**
 * Holds a request without ever answering it: `expectRequest(method) { unanswered.hold() }`.
 * The handler is cancelled when the client sends `$/cancelRequest`, or when the test ends; [isCancelled] turns true then.
 */
internal class UnansweredRequest {
  private val cancelled = CompletableDeferred<Unit>()

  val isCancelled: Boolean get() = cancelled.isCompleted

  suspend fun hold(): Nothing {
    try {
      awaitCancellation()
    }
    finally {
      cancelled.complete(Unit)
    }
  }
}

/**
 * The expectations of a test on one fake server, and the messages that the test sends from it.
 *
 * A session adds its expectations to [expectations] right away.
 * Everything else needs the started server, so it calls [startedServer], which throws until the server has started.
 */
internal class FakeLspServerSession(
  private val expectations: ExpectationHolder,
  private val pendingExpectations: MutableCollection<Pair<String, Deferred<Unit>>>,
  private val startedServer: () -> FakeLspServer,
) : ServerSessionProtocolScope() {
  /** The capabilities that the client sent in `initialize`. */
  val clientCapabilities: ClientCapabilities
    get() = checkNotNull(startedServer().initializeParams) { "The client has not sent `initialize` yet" }.capabilities

  /** The URI that the client uses for [file], a path relative to the project directory. */
  fun fileUri(file: String): String = startedServer().fileUri(file) ?: error("The client has not sent `initialize` yet")

  /**
   * The returned [Deferred] completes when a request matching [predicate] arrives, before [handler] starts.
   * The fake server answers the request with the value [handler] returns.
   *
   * The handler can suspend to hold the response, or throw a [ResponseErrorException] to answer with an error.
   * A `$/cancelRequest` from the client cancels the handler; [UnansweredRequest] lets a test assert that.
   * An expectation that never matches fails the test, unless the test cancels the returned [Deferred].
   */
  fun <Params : Any, Response> expectRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean = { true },
    handler: suspend (Params) -> Response,
  ): Deferred<Unit> {
    val deferred = newExpectationDeferred(method.method)
    @Suppress("UNCHECKED_CAST")
    val expectedRequest = ExpectedRequest(method.method, { predicate(it as Params) }, { handler(it as Params) }, deferred)
    expectations.addRequest(expectedRequest)
    deferred.invokeOnCompletion { if (it != null) expectations.removeRequest(expectedRequest) }
    return deferred
  }

  fun <Params : Any> expectNotification(
    method: ClientToServerLspNotification<Params>,
    predicate: (Params) -> Boolean = { true },
  ): Deferred<Unit> {
    val deferred = newExpectationDeferred(method.method)
    @Suppress("UNCHECKED_CAST")
    val expectedNotification = ExpectedNotification(method.method, { predicate(it as Params) }, deferred)
    expectations.addNotification(expectedNotification)
    deferred.invokeOnCompletion { if (it != null) expectations.removeNotification(expectedNotification) }
    return deferred
  }

  private fun newExpectationDeferred(method: String): CompletableDeferred<Unit> =
    CompletableDeferred<Unit>().also { pendingExpectations.add(method to it) }

  /** Sends the request and waits for the response of the client. */
  fun <Params : Any, Response> sendRequest(method: ServerToClientLspRequest<Params, Response>, paramsBuilder: () -> Params): Response =
    method.send(startedServer().remoteLanguageClient, paramsBuilder()).get(30, TimeUnit.SECONDS)

  fun <Params : Any> sendNotification(method: ServerToClientLspNotification<Params>, paramsBuilder: () -> Params) {
    method.send(startedServer().remoteLanguageClient, paramsBuilder())
  }

  /** Ends the server process without the LSP shutdown, as a crash does. */
  fun terminateProcess() {
    startedServer().terminate()
  }
}
