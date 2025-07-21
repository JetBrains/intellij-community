// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Binds entity lifecycle to a coroutine.
 * The entity is deleted once [block] returns, either with an exception or with a value.
 *
 * A typical usage pattern:
 * ```
 * change {
 *   MyEntity.new(...)
 * }.useEntity { myEntity ->
 *   awaitCancellation()
 *   // or collect an infinite `Flow`, or `launchOnEach` on a `Query`
 * }
 * ```
 */
suspend fun <E : Entity, R> E.useEntity(
  block: suspend CoroutineScope.(E) -> R,
): R {
  val e = this@useEntity
  try {
    return coroutineScope {
      block(e)
    }
  }
  finally {
    withContext(NonCancellable) {
      change {
        e.delete()
      }
    }
  }
}
