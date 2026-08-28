package com.intellij.platform.lsp.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

internal suspend fun CoroutineScope.configureServerSession(
  project: Project,
  file: VirtualFile,
): ServerSession {
  awaitFileOpenedByLspServer(project, file)
  return currentServerSession(project)
}

/** The session of the already started fake server. Use when no local file open can settle the start. */
internal fun CoroutineScope.currentServerSession(project: Project): ServerSession {
  val servers = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
  val descriptor = servers.first().descriptor as FakeLspServerDescriptor
  return ServerSessionImpl(descriptor, this)
}

internal abstract class ServerSession : ServerSessionProtocolScope(), CoroutineScope {
  abstract fun fileUri(file: VirtualFile): String

  abstract suspend fun <Params : Any, Response> awaitRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean = { true },
    result: (Params) -> Response,
  )

  abstract fun <Params : Any, Response> expectRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean = { true },
    result: (Params) -> Response,
  ): Deferred<Unit>

  /**
   * Like [expectRequest], but [result] returns a [CompletableFuture] that the fake server sends back as is.
   * A test can keep the future pending to delay the response, complete it exceptionally to simulate a server
   * error, or assert [CompletableFuture.isCancelled] to observe a `$/cancelRequest` from the client.
   */
  abstract fun <Params : Any, Response> expectRequestAsync(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean = { true },
    result: (Params) -> CompletableFuture<Response>,
  ): Deferred<Unit>

  abstract suspend fun <Params : Any> awaitNotification(
    method: ClientToServerLspNotification<Params>,
    predicate: (Params) -> Boolean = { true },
  )

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

  abstract suspend fun awaitExpected()
}

internal data class ExpectedNotification(
  val method: String,
  val predicate: (Any?) -> Boolean,
  val deferred: CompletableDeferred<Unit>,
)

internal data class ExpectedRequest(
  val method: String,
  val predicate: (Any?) -> Boolean,
  val resultProvider: (Any?) -> Any?,
  val deferred: CompletableDeferred<Unit>,
)

private class ServerSessionImpl(
  private val descriptor: FakeLspServerDescriptor,
  scope: CoroutineScope,
) : ServerSession(), CoroutineScope by scope {
  private val server = descriptor.server
  private val expectations = server.expectations
  private val pendingExpectations = mutableListOf<Deferred<Unit>>()

  override fun fileUri(file: VirtualFile): String = descriptor.getFileUri(file)

  override suspend fun <Params : Any, Response> awaitRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean,
    result: (Params) -> Response,
  ) {
    expectRequest(method, predicate, result).await()
  }

  override fun <Params : Any, Response> expectRequest(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean,
    result: (Params) -> Response,
  ): Deferred<Unit> = doExpectRequest(method, predicate, result)

  override fun <Params : Any, Response> expectRequestAsync(
    method: ClientToServerLspRequest<Params, Response>,
    predicate: (Params) -> Boolean,
    result: (Params) -> CompletableFuture<Response>,
  ): Deferred<Unit> = doExpectRequest(method, predicate, result)

  private fun <Params : Any> doExpectRequest(
    method: ClientToServerLspRequest<Params, *>,
    predicate: (Params) -> Boolean,
    result: (Params) -> Any?,
  ): Deferred<Unit> {
    val deferred = CompletableDeferred<Unit>()
    @Suppress("UNCHECKED_CAST")
    val expectedRequest = ExpectedRequest(method.method, { predicate(it as Params) }, { result(it as Params) }, deferred)

    expectations.addRequest(expectedRequest)

    val asyncDeferred = async {
      try {
        deferred.await()
      }
      catch (e: Exception) {
        expectations.removeRequest(expectedRequest)
        throw e
      }
    }
    pendingExpectations.add(asyncDeferred)
    return asyncDeferred
  }

  override suspend fun <Params : Any> awaitNotification(
    method: ClientToServerLspNotification<Params>,
    predicate: (Params) -> Boolean,
  ) {
    expectNotification(method, predicate).await()
  }

  override fun <Params : Any> expectNotification(
    method: ClientToServerLspNotification<Params>,
    predicate: (Params) -> Boolean,
  ): Deferred<Unit> {
    val deferred = CompletableDeferred<Unit>()
    @Suppress("UNCHECKED_CAST")
    val expectedNotification = ExpectedNotification(method.method, { predicate(it as Params) }, deferred)

    expectations.addNotification(expectedNotification)

    val asyncDeferred = async {
      try {
        deferred.await()
      }
      catch (e: Exception) {
        expectations.removeNotification(expectedNotification)
        throw e
      }
    }
    pendingExpectations.add(asyncDeferred)
    return asyncDeferred
  }

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
