// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.core.RpcMessage
import fleet.util.logging.KLoggers
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val logger by lazy { KLoggers.logger("fleet.rpc.server.SlowCallLogging") }

/**
 * See `RPC_TIMEOUT` in `fleet.rpc.client.RpcClient`, one minute at the moment of writing.
 * A call that spends this long on the server is already on its way to being timed out by the client.
 */
private val DEFAULT_SLOW_CALL_THRESHOLD = 1.minutes

/**
 * Reports calls that this side of the connection serves suspiciously slowly.
 *
 * The client aborts a call after `RPC_TIMEOUT` and only reports the fact of the timeout, while the server cancels
 * the implementation silently, so without this middleware there is no way to tell whether a timed out call was slow
 * to execute, slow to be delivered, or never served at all. Every report contains the request id, which is also
 * part of the client-side [fleet.rpc.client.RpcTimeoutException] message, so both sides can be matched in the logs.
 *
 * @param threshold calls faster than this are not reported
 * @param report receives the message to log, overridable for tests
 */
fun slowCallLoggingMiddleware(
  threshold: Duration = DEFAULT_SLOW_CALL_THRESHOLD,
  report: (String) -> Unit = { message -> logger.error { message } },
): RpcExecutorMiddleware =
  object : RpcExecutorMiddleware {
    override suspend fun execute(
      request: RpcMessage.CallRequest,
      execute: suspend (RpcMessage.CallRequest) -> RpcMessage,
    ): RpcMessage {
      val start = TimeSource.Monotonic.markNow()
      val result = try {
        execute(request)
      }
      catch (ex: CancellationException) {
        // the client cancels the request when it gives up on it, this is what its timeout looks like from here
        reportIfSlow(start, threshold, report) { elapsed ->
          "RPC call ${request.callId} was cancelled while running, it ran for $elapsed"
        }
        throw ex
      }
      catch (ex: Throwable) {
        reportIfSlow(start, threshold, report) { elapsed ->
          "Slow RPC call ${request.callId} has failed after $elapsed with ${ex::class.simpleName}"
        }
        throw ex
      }
      reportIfSlow(start, threshold, report) { elapsed ->
        "Slow RPC call ${request.callId} took $elapsed"
      }
      return result
    }
  }

/**
 * Matches the way the client names a call in [fleet.rpc.client.RpcTimeoutException], modulo the request id,
 * which only the log gets.
 */
private val RpcMessage.CallRequest.callId: String
  get() = "${classMethodDisplayName()}[#$requestId]"

private inline fun reportIfSlow(
  start: TimeSource.Monotonic.ValueTimeMark,
  threshold: Duration,
  report: (String) -> Unit,
  message: (Duration) -> String,
) {
  val elapsed = start.elapsedNow()
  if (elapsed >= threshold) {
    report(message(elapsed))
  }
}
