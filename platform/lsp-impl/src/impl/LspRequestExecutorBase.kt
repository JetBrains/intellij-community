package com.intellij.platform.lsp.impl

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient.Companion.DEFAULT_REQUEST_TIMEOUT_MS
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@ApiStatus.Internal
abstract class LspRequestExecutorBase(private val lspClient: LspClientImpl) {
  private val executorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("LSP Executor: " + lspClient.descriptor, 1)

  internal fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit) = synchronized(executorService) {
    if (!executorService.isShutdown) {
      executorService.execute { lsp4jSender(lspClient.lsp4jServer) }
    }
  }

  internal suspend fun <Lsp4jResponse> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>): Lsp4jResponse? {
    return doSendRequestAsync(lsp4jSender)
      ?.await()
  }

  @RequiresBackgroundThread
  internal fun <Lsp4jResponse> sendRequestSync(
    timeoutMs: Int = DEFAULT_REQUEST_TIMEOUT_MS,
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
  ): Lsp4jResponse? {
    return doSendRequestAsync(lsp4jSender)
      ?.awaitWithCheckCanceled(cancelOnPCE = true, lsp4jSender.javaClass.name, timeoutMs)
  }

  @RequiresBackgroundThread
  protected fun <T> CompletableFuture<T?>.awaitSync(timeoutMs: Int = DEFAULT_REQUEST_TIMEOUT_MS): T? {
    return awaitWithCheckCanceled(cancelOnPCE = true, "awaitSync", timeoutMs)
  }

  /**
   * This function sends a request to the LSP server and tries to wait for the response synchronously. If synchronous waiting gets
   * canceled, the function switches to the asynchronous mode. In more detail:
   *  - Sends a request to the LSP server and waits for the response for no more than [DEFAULT_REQUEST_TIMEOUT_MS] ms.
   *    Waiting is cancelable (thanks to regular [ProgressManager.checkCanceled] calls)
   *  - If synchronous waiting is canceled, this function returns immediately, but keeps waiting for the response in background
   *  - Once a response is received, it's passed to the [lsp4jResponseConsumer]
   *  - In several cases the `lsp4jResponseConsumer` may receive null:
   *    - this `LspServer` is not running or not initialized yet
   *    - server returns `null` response
   *    - server responds with an error (the error will appear in IDE logs)
   *
   *  The use case is the following: even if waiting for the response is canceled, later the client will need the response anyway.
   *  So this function, which switches to async mode, helps not to send the same request once again.
   */
  internal fun <Lsp4jResponse> sendRequestAsyncButWaitForResponseWithCheckCanceled(
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
    lsp4jResponseConsumer: (Lsp4jResponse?) -> Unit,
  ) {
    doSendRequestAsyncAndConsumeResponse(lsp4jSender, lsp4jResponseConsumer)
      ?.awaitWithCheckCanceled(cancelOnPCE = false, lsp4jSender.javaClass.name, DEFAULT_REQUEST_TIMEOUT_MS)
  }

  /**
   * Calls [doSendRequestAsync] and passes the result of the [CompletableFuture] to the [lsp4jResponseConsumer].
   *
   * *Note:* don't expose [CompletableFuture] as a return type to the public API.
   */
  private fun <Lsp4jResponse> doSendRequestAsyncAndConsumeResponse(
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
    lsp4jResponseConsumer: (Lsp4jResponse?) -> Unit,
  ): CompletableFuture<Lsp4jResponse?>? {
    val future: CompletableFuture<Lsp4jResponse?>? = doSendRequestAsync(lsp4jSender)
    if (future == null) {
      lsp4jResponseConsumer(null)
      return null
    }
    return future.whenComplete { result: Lsp4jResponse?, _: Throwable? -> lsp4jResponseConsumer(result) }
  }

  /**
   * Sends a request to the LSP server asynchronously. Note that `null` is a valid response from the server, so
   * [CompletableFuture.get] may return `null`.
   *
   * *Note:* don't expose [CompletableFuture] as a return type to the public API.
   *
   * @return `null` if the current state of this `LspServer` doesn't allow sending requests.
   */
  protected fun <Lsp4jResponse> doSendRequestAsync(
    lsp4jSender: (Lsp4jServer) -> CompletableFuture<Lsp4jResponse>,
  ): CompletableFuture<Lsp4jResponse?>? {
    if (lspClient.state != LspServerState.Running) {
      lspClient.logDebug("Server not initialized yet, skipping request ${lsp4jSender.javaClass.name}")
      return null
    }

    synchronized(executorService) {
      if (executorService.isShutdown) return null

      val resultFuture = CompletableFuture<Lsp4jResponse?>()

      executorService.execute {
        val lsp4jResponseFuture = lsp4jSender(lspClient.lsp4jServer)

        lsp4jResponseFuture.whenComplete { lsp4jResponse: Lsp4jResponse?, th: Throwable? ->
          if (th == null) {
            resultFuture.complete(lsp4jResponse)
          }
          else {
            resultFuture.completeExceptionally(th)
          }
        }

        // Propagate cancellation of the outer `resultFuture` to the inner `lsp4jResponseFuture`
        resultFuture.whenComplete { _, _ ->
          if (resultFuture.isCancelled) sendCancelRequest(lsp4jResponseFuture)
        }
      }

      return resultFuture
    }
  }

  /**
   * Sends [$/cancelRequest](https://microsoft.github.io/language-server-protocol/specification/#cancelRequest)
   * notification to the LSP server
   *
   * @param lsp4jResponseFuture [CompletableFuture] returned by one of the functions of the [Lsp4jServer] class
   */
  private fun sendCancelRequest(lsp4jResponseFuture: CompletableFuture<*>) = synchronized(executorService) {
    if (!executorService.isShutdown) {
      executorService.execute {
        lsp4jResponseFuture.cancel(true)
      }
    }
  }

  /**
   * Waits for the [this] future completion for up to [timeoutMs] ms and returns its result.
   * Calls [ProgressManager.checkCanceled] every 10 ms.
   *  - returns `null` if `future` doesn't complete in [timeoutMs] ms
   *  - returns `null` if `future` completes exceptionally, writes the exception to the log file
   *  - other exceptions are not handled, so thrown as is
   *
   *  @param cancelOnPCE whether [future.cancel()][Future.cancel] should be called if [ProcessCanceledException] happens
   *                     while waiting for the server response
   */
  @RequiresBackgroundThread
  private fun <T> Future<T?>.awaitWithCheckCanceled(cancelOnPCE: Boolean, debugName: String, timeoutMs: Int): T? {
    val future = this
    val t0 = System.currentTimeMillis()

    while (System.currentTimeMillis() - t0 < timeoutMs) {
      try {
        ProgressManager.checkCanceled()
      }
      catch (pce: ProcessCanceledException) {
        if (cancelOnPCE) {
          future.cancel(true)
        }
        throw pce
      }

      try {
        return future.get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      }
      catch (_: TimeoutException) { /* ok */
      }
      catch (e: ExecutionException) {
        lspClient.logWarn("Error response from server: ${e.cause}")
        return null
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
    }

    lspClient.logInfo("No response from the server in ${timeoutMs}ms for: $debugName")
    future.cancel(true)
    return null
  }

  internal fun shutdownNow() {
    synchronized(executorService) {
      executorService.shutdownNow()
    }

    afterShutdown()
  }

  abstract fun afterShutdown()
}
