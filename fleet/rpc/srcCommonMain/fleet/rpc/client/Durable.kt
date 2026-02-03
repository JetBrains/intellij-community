// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.util.causeOfType
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlin.math.min

/**
 * retries [body] if it fails because of [RpcClientException]
 *
 * calls to rpc proxies suspend until new socket connection is established/service provider becomes available
 * so there is no need in additional delays
 *
 * be aware that there is no at-most-once guarantee, your calls should be idempotent
 */
suspend fun <T> durable(verbose: Boolean = false, body: suspend CoroutineScope.() -> T): T {
  fun logRetry(t: Throwable? = null, msg: () -> Any?) {
    if (verbose) {
      DurableLogger.logger.info(t, msg)
    }
    else {
      DurableLogger.logger.trace(t, msg)
    }
  }
  var resolveDelay = INITIAL_RETRY_DELAY
  var resolveAttempt = 0
  while (true) {
    currentCoroutineContext().job.ensureActive()
    try {
      return coroutineScope { body() }
    }
    catch (ex: Throwable) {
      when (val clientException = ex.causeOfType(RpcClientException::class)) {
        null -> {
          logRetry(ex) { "durable will not retry" }
          throw ex
        }
        is UnresolvedServiceException -> {
          /**
           * Workaround for a design flaw.
           *
           * A privimive of discoverability on network level is Route. We intentionally leave topology discovery to higher level protocols.
           * The problem is that there is no causality between higher level communication and a particular rpc call.
           * If a service is gone, discovery routine will be notified _eventually_, but the call is failing right now.
           * We cannot act on this knowledge to update shared topology, because our timelines are not in sync,
           * it might be that the service has re-appeared and the discovery routine is already ahead of us.
           *
           * Instead we are going to spin here, one of the two should happen:
           * - topology will cancel the calling coroutine (if it is properly structured, see [withEntities])
           * - service will become available again and next call will succeed
           * */
          resolveAttempt++
          resolveDelay = when {
            resolveAttempt < RETRY_BEFORE_BACKOFF -> INITIAL_RETRY_DELAY
            resolveAttempt == RETRY_BEFORE_BACKOFF -> {
              DurableLogger.logger.error { "Service ${clientException.serviceId} is unresolved after 1 minute" }
              INITIAL_RETRY_DELAY
            }
            // After first minute: exponential back-off up to 10 seconds
            else -> min(resolveDelay * 2, MAX_RETRY_DELAY)
          }
          logRetry { "Service ${clientException.serviceId} is unresolved, will try again" }
          delay(resolveDelay)
        }
      }
      logRetry(ex) { "durable will retry" }
    }
  }
}

private const val RETRY_BEFORE_BACKOFF = 600 // 1 minute with 100ms delay
private const val INITIAL_RETRY_DELAY = 100L
private const val MAX_RETRY_DELAY = 10000L // 10 seconds

private object DurableLogger {
  val logger = logger<DurableLogger>()
}
