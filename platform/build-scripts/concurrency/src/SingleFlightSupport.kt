package com.intellij.platform.buildScripts.concurrency

import org.jetbrains.annotations.ApiStatus.Internal

private val activeSingleFlightOwners = ThreadLocal.withInitial<Set<Any>> { emptySet() }

@Internal
fun currentSingleFlightOwners(): Set<Any> = activeSingleFlightOwners.get()

@Internal
fun <T> withSingleFlightOwners(inherited: Set<Any>, owner: Any?, body: () -> T): T {
  val previous = activeSingleFlightOwners.get()
  activeSingleFlightOwners.set(if (owner == null) inherited else inherited + owner)
  try {
    return body()
  }
  finally {
    if (previous.isEmpty()) activeSingleFlightOwners.remove() else activeSingleFlightOwners.set(previous)
  }
}

@Internal
fun checkRecursiveSingleFlightAwait(owner: Any, operationName: String, completed: Boolean) {
  check(completed || owner !in activeSingleFlightOwners.get()) { "Recursive await of '$operationName' detected" }
}
