// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.ChangeScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield

/**
 * Points to the value of the latest recalculation during the rebase.
 */
class ReplayingValue<T>(private var valueInternal: T? = null) {
  var value: T
    @Suppress("UNCHECKED_CAST")
    get() = valueInternal as T
    set(value) {
      valueInternal = value
    }

  suspend fun await(): T {
    awaitCommitted()
    return value
  }
}

fun <T> ChangeScope.sharedRead(f: SharedChangeScope.() -> T): ReplayingValue<T> {
  val box = ReplayingValue<T>()
  shared {
    box.value = f()
  }
  return box
}

suspend fun awaitCommitted() {
  val deferred = CompletableDeferred<Job>()
  change {
    shared {
      effect {
        deferred.complete(meta[DeferredChangeKey]!!)
      }
    }
  }
  deferred.await().join()
  yield()
}