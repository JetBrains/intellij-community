// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import fleet.reporting.shared.runtime.currentSpan
import fleet.reporting.shared.tracing.spannedScope
import fleet.util.async.*
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private object Saga {
  val logger = logger<Saga>()
}

/**
 * Is used to launch a coroutine with entities and spans.
 *
 * We often write code as a sequence of changes and read operations within this coroutine.
 * The saga function may take vararg-entities as arguments, and in this case guarantees that
 * these entities exist in the database until the coroutine is completed (otherwise, it throws an exception).
 */
fun <T> CoroutineScope.saga(
  vararg requiredEntities: Entity,
  name: String = "anonymous saga",
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  f: suspend CoroutineScope.() -> T,
): Deferred<T> {
  val outerSpan = currentSpan
  return async(coroutineContext) {
    catching {
      spannedScope(name, info = {
        cause = outerSpan
      }) {
        withEntities(*requiredEntities) {
          f()
        }
      }
    }.onFailure { e ->
      // according to [kotlinx.coroutines.CoroutineExceptionHandler] docs, a coroutine that was created using async
      // always catches all its exceptions and represents them in the resulting [Deferred] object,
      // so we have to log an exception manually
      Saga.logger.error(e) { "Exception in saga" }
    }.getOrThrow()
  }
}
