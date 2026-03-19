// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private class ActiveSingleFlightComputations(
  private val activeOwners: Set<Any>,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ActiveSingleFlightComputations>

  fun contains(owner: Any): Boolean = owner in activeOwners

  fun add(owner: Any): ActiveSingleFlightComputations = ActiveSingleFlightComputations(activeOwners + owner)
}

@Internal
fun singleFlightComputationContext(currentContext: CoroutineContext, owner: Any): CoroutineContext {
  val activeComputations = currentContext[ActiveSingleFlightComputations]
  return activeComputations?.add(owner) ?: ActiveSingleFlightComputations(setOf(owner))
}

@Internal
fun checkRecursiveSingleFlightAwait(
  currentContext: CoroutineContext,
  owner: Any,
  operationName: String,
  deferred: CompletableDeferred<*>,
) {
  check(deferred.isCompleted || currentContext[ActiveSingleFlightComputations]?.contains(owner) != true) {
    "Recursive await of '$operationName' detected"
  }
}
