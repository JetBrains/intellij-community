// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.Entity
import fleet.kernel.rete.ContextMatches
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

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
    /**
     * Consider the following situation:
     * ```
     * E1.each().launchOnEach {
     *   change {
     *     E2.new(...)
     *   }.useEntity {
     *     awaitCancellation()
     *   }
     * }
     * ```
     * Deletion of `E1` triggers the cancellation of `launchOnEach` block,
     * which in turn cancels `awaitCancellation`,
     * and we get into the current `finally` block trying to delete `E2`.
     * At this point we'll get the exception `UnsatisfiedMatchException: match invalidated by rete`
     * where the match is the `E1` entity, and the match was indeed invalidated because `E1` was just deleted.
     *
     * But now we are in a situation where we can't clean up `E2`.
     * To avoid `UnsatisfiedMatchException` we remove `ContextMatches` from the current coroutine context.
     * This is not safe in general, but here we control the code,
     * and we know that `E2` is being deleted and that's it.
     */
    val contextMatches = currentCoroutineContext()[ContextMatches]
    val withoutContextMatches = contextMatches?.copy(matches = persistentListOf())
                                ?: EmptyCoroutineContext
    withContext(NonCancellable + withoutContextMatches) {
      change {
        e.delete()
      }
    }
  }
}
