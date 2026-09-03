// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.asContextElement
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The single-flight computations the current thread runs inside.
 *
 * A thread local, because a shared computation starts on a thread of its own and its caller may be a plain blocking
 * one. [singleFlightContextElement] carries the set across the resumes of a coroutine.
 */
private val activeSingleFlightOwners: ThreadLocal<Set<Any>> = ThreadLocal.withInitial { emptySet() }

/** The owners to hand to a computation that the current thread starts on a thread of its own. */
@Internal
fun currentSingleFlightOwners(): Set<Any> = activeSingleFlightOwners.get()

/**
 * Runs [body] inside the computations of [inherited], and of [owner] when it is not `null`.
 *
 * A shared computation calls this on its own thread with the owners its caller had.
 */
@Internal
fun <T> withSingleFlightOwners(inherited: Set<Any>, owner: Any?, body: () -> T): T {
  val previous = activeSingleFlightOwners.get()
  activeSingleFlightOwners.set(if (owner == null) inherited else inherited + owner)
  try {
    return body()
  }
  finally {
    activeSingleFlightOwners.set(previous)
  }
}

/**
 * Reinstalls the owners of the calling thread on every thread a coroutine resumes on.
 *
 * `runBlockingOnVirtualThreads` adds it, so a loader that goes back into coroutines keeps the guard. Empty when the
 * caller runs inside no computation, so an ordinary build entry adds no element.
 */
@Internal
fun singleFlightContextElement(): CoroutineContext {
  val owners = activeSingleFlightOwners.get()
  return if (owners.isEmpty()) EmptyCoroutineContext else activeSingleFlightOwners.asContextElement(owners)
}

/**
 * Marks a coroutine as the computation of [owner], on top of the computations of the calling thread.
 *
 * For a cache that runs its loader in a coroutine and not on a thread of its own. A loader on a thread of its own
 * uses [withSingleFlightOwners].
 */
@Internal
fun singleFlightComputationContext(owner: Any): CoroutineContext {
  return activeSingleFlightOwners.asContextElement(activeSingleFlightOwners.get() + owner)
}

/** Fails when the caller runs inside the computation of [owner] and that computation is not [completed] yet. */
@Internal
fun checkRecursiveSingleFlightAwait(owner: Any, operationName: String, completed: Boolean) {
  check(completed || owner !in activeSingleFlightOwners.get()) {
    "Recursive await of '$operationName' detected"
  }
}
