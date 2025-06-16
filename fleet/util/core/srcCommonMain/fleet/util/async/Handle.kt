// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import fleet.util.updateAndGet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.CoroutineContext

data class Handle<T>(val value: Deferred<ValueWithContext<T>>,
                     val shutdownSignal: CompletableJob,
                     val job: Job) {

  suspend fun await(): T = value.await().value

  suspend fun shutdownAndWait() {
    shutdownSignal.complete()
    if (value.isCompleted) {
      job.join()
    }
    else {
      job.cancelAndJoin()
    }
  }

  fun scheduleShutdown() {
    shutdownSignal.complete()
  }
}

typealias Process<T> = suspend CoroutineScope.(T) -> Unit
typealias Launcher<T> = Process<Process<T>>

data class ValueWithContext<T>(val value: T,
                               val context: CoroutineContext) {
  suspend fun within(f: suspend CoroutineScope.(T) -> Unit) = withContext(context) { f(value) }
}

@Deprecated("use resource instead")
fun <T> CoroutineScope.handle(launcher: Launcher<T>): Handle<T> {
  val value = CompletableDeferred<ValueWithContext<T>>()
  val shutdownSignal = Job()
  val job = launch {
    launcher { process ->
      value.complete(ValueWithContext(process, currentCoroutineContext()))
      shutdownSignal.join()
    }
  }
  job.invokeOnCompletion { ex ->
    value.completeExceptionally(ex ?: RuntimeException("job has completed without invoking body"))
  }
  return Handle(value, shutdownSignal, job)
}

suspend fun <T> Handle<T>.use(body: suspend CoroutineScope.(Deferred<T>) -> Unit) {
  try {
    coroutineScope { body(async { value.await().value }) }
  }
  finally {
    shutdownAndWait()
  }
}

interface HandleScope : CoroutineScope {
  fun <T> handle(launcher: Launcher<T>): Handle<T>
}

suspend fun<T> handleScope(body: suspend HandleScope.() -> T): T =
  supervisorScope {
    handleScopeImpl(this, body)
  }

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.testlib"])
suspend fun handleScopeNonSupervising(body: suspend HandleScope.() -> Unit) {
  coroutineScope {
    handleScopeImpl(this, body)
  }
}

private suspend fun<T> handleScopeImpl(outerScope: CoroutineScope, body: suspend HandleScope.() -> T): T {
  val handles = AtomicReference(persistentSetOf<Handle<*>>())
  return try {
    coroutineScope {
      val context = coroutineContext
      object : HandleScope {
        override val coroutineContext: CoroutineContext get() = context
        override fun <T> handle(launcher: Launcher<T>): Handle<T> {
          val handle = outerScope.handle(launcher)
          handles.updateAndGet { hs -> hs.add(handle) }
          handle.job.invokeOnCompletion { handles.updateAndGet { hs -> hs.remove(handle) } }
          return handle
        }
      }.body()
    }
  }
  finally {
    handles.load().forEach { h -> outerScope.launch { h.shutdownAndWait() } }
  }
}