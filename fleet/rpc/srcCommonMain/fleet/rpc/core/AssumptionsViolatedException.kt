// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.rpc.client.RpcClientException
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout

class AssumptionsViolatedException(msg: String? = null)
  : RpcClientException(if (msg == null) "Conflict" else "Conflict: $msg", null),
    CopyableThrowable<AssumptionsViolatedException> {
  override fun createCopy(): AssumptionsViolatedException {
    return AssumptionsViolatedException(message)
  }
}

suspend fun <T> retry(block: suspend CoroutineScope.() -> T): T {
  return withTimeout(30_000) {
    var counter = 0
    while (true) {
      try {
        return@withTimeout block()
      }
      catch (ex: AssumptionsViolatedException) {
        if (counter++ > 30) throw RuntimeException("Exceeded retry count", ex)
      }
      catch (ex: RpcException) {
        require(ex.failure.conflict == null)
        throw ex
      }
    }
    throw RuntimeException("unreachable")
  }
}