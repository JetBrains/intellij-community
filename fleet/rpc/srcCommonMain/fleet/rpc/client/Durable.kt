// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.util.causeOfType
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/**
 * retries [body] if it fails because of [RpcClientException]
 *
 * calls to rpc proxies suspend until new socket connection is established/service provider becomes available
 * so there is no need in additional delays
 *
 * be aware that there is no at-most-once guarantee, your calls should be idempotent
 */
suspend fun <T> durable(verbose: Boolean = false, body: suspend CoroutineScope.() -> T): T {
  fun logRetry(t: Throwable?, msg: () -> Any?) {
    if (verbose) {
      DurableLogger.logger.info(t, msg)
    }
    else {
      DurableLogger.logger.trace(t, msg)
    }
  }
  while (true) {
    coroutineContext.job.ensureActive()
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
          DurableLogger.logger.warn { "Service ${clientException.serviceId} is unresolved, will try again" }
          delay(100)
        }
      }
      logRetry(ex) { "durable will retry" }
    }
  }
}

private object DurableLogger {
  val logger = logger<DurableLogger>()
}
