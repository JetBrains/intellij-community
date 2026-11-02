package com.intellij.platform.lsp.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job

internal suspend fun CoroutineScope.configureServerSession(
  project: Project,
  file: VirtualFile,
): FakeLspServerSession {
  awaitFileOpenedByLspServer(project, file)
  return currentServerSession(project)
}

/** The session of the already started fake server. Use when no local file open can settle the start. */
internal fun CoroutineScope.currentServerSession(project: Project): FakeLspServerSession {
  val clients = LspClientManager.getInstance(project).getClients(FakeLspIntegrationProvider::class.java)
  val descriptor = clients.first().descriptor as FakeLspClientDescriptor
  return FakeLspServerSessionImpl(descriptor, this)
}

internal abstract class FakeLspServerSession : ServerSessionProtocolScope(), CoroutineScope {
  abstract fun fileUri(file: VirtualFile): String

  /**
   * The returned [Deferred] completes when a request matching [predicate] arrives, before [handler] starts.
   * The fake server answers the request with the value [handler] returns.
   *
   * The handler can suspend to hold the response, send requests and notifications of its own before answering,
   * or throw a [org.eclipse.lsp4j.jsonrpc.ResponseErrorException] to answer with an error. Any other exception is logged as an error.
   * A `$/cancelRequest` from the client cancels the handler; [UnansweredRequest] lets a test assert that.
   * Handlers still running when the server stops are cancelled.
   */
  abstract fun <Params : Any, Response> expectRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean = { true },
    handler: suspend (Params) -> Response,
  ): Deferred<Unit>

  abstract fun <Params : Any> expectNotification(
    method: ClientToServerLspNotification<Params>,
    predicate: (Params) -> Boolean = { true },
  ): Deferred<Unit>

  abstract suspend fun <Params : Any, Response> sendRequest(
    method: ServerToClientLspRequest<Params, Response>,
    paramsBuilder: () -> Params,
  ): Response

  abstract fun <Params : Any> sendNotification(
    method: ServerToClientLspNotification<Params>,
    paramsBuilder: () -> Params,
  )

  /** Writes [text] to the stderr of the fake server process. The IDE reads each call as one chunk. */
  abstract fun writeErrorOutput(text: String)

  /** Writes [text] to the stdout of the fake server process. Only a server that talks LSP over a socket has a free stdout. */
  abstract fun writeStandardOutput(text: String)

  /** Terminates the fake server process without the LSP shutdown, as a crash does. */
  abstract fun terminateProcess()

  abstract suspend fun awaitExpected()
}

/**
 * Holds a request without ever answering it: `expectRequest(method) { unanswered.hold() }`.
 * The handler is cancelled when the client sends `$/cancelRequest`, or when the server stops; [isCancelled] turns true then.
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

internal data class ExpectedNotification(
  val method: String,
  val predicate: (Any?) -> Boolean,
  val deferred: CompletableDeferred<Unit>,
)

internal data class ExpectedRequest(
  val method: String,
  val predicate: (Any?) -> Boolean,
  val handler: suspend (Any?) -> Any?,
  val deferred: CompletableDeferred<Unit>,
)

private class FakeLspServerSessionImpl(
  private val descriptor: FakeLspClientDescriptor,
  scope: CoroutineScope,
) : FakeLspServerSession(), CoroutineScope by scope {
  private val server = descriptor.server
  private val expectations = server.expectations
  private val pendingExpectations = mutableListOf<Deferred<Unit>>()

  override fun fileUri(file: VirtualFile): String = descriptor.getFileUri(file)

  override fun writeErrorOutput(text: String) {
    server.writeErrorOutput(text)
  }

  override fun writeStandardOutput(text: String) {
    val socketServer = checkNotNull(server as? FakeSocketLspServer) { "The stdout of a server without a socket carries LSP" }
    socketServer.writeStandardOutput(text)
  }

  override fun terminateProcess() {
    server.destroy()
  }

  override fun <Params : Any, Response> expectRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean,
    handler: suspend (Params) -> Response,
  ): Deferred<Unit> {
    val deferred = newExpectationDeferred()
    @Suppress("UNCHECKED_CAST")
    val expectedRequest = ExpectedRequest(method.method, { predicate(it as Params) }, { handler(it as Params) }, deferred)

    expectations.addRequest(expectedRequest)
    deferred.invokeOnCompletion { if (it != null) expectations.removeRequest(expectedRequest) }
    return deferred
  }

  override fun <Params : Any> expectNotification(
    method: ClientToServerLspNotification<Params>,
    predicate: (Params) -> Boolean,
  ): Deferred<Unit> {
    val deferred = newExpectationDeferred()
    @Suppress("UNCHECKED_CAST")
    val expectedNotification = ExpectedNotification(method.method, { predicate(it as Params) }, deferred)

    expectations.addNotification(expectedNotification)
    deferred.invokeOnCompletion { if (it != null) expectations.removeNotification(expectedNotification) }
    return deferred
  }

  /**
   * The fake server completes the returned deferred itself, as soon as a message matches, so it is completed before the handler starts.
   * It is a child of the test's job: an expectation that never matches keeps the test running until it is cancelled.
   */
  private fun newExpectationDeferred(): CompletableDeferred<Unit> =
    CompletableDeferred<Unit>(coroutineContext.job).also { pendingExpectations.add(it) }

  override suspend fun <Params : Any, Response> sendRequest(
    method: ServerToClientLspRequest<Params, Response>,
    paramsBuilder: () -> Params,
  ): Response {
    return method.send(server.remoteLanguageClient, paramsBuilder()).await()
  }

  override fun <Params : Any> sendNotification(
    method: ServerToClientLspNotification<Params>,
    paramsBuilder: () -> Params,
  ) {
    method.send(server.remoteLanguageClient, paramsBuilder())
  }

  override suspend fun awaitExpected() {
    pendingExpectations.awaitAll()
  }
}
