// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <T : Job, R> T.use(body: suspend CoroutineScope.(T) -> R): R {
  return try {
    coroutineScope { body(this@use) }
  }
  finally {
    cancelAndJoin()
  }
}

suspend fun <T> useAll(vararg jobs: Job, body: suspend CoroutineScope.() -> T): T {
  return try {
    coroutineScope(body)
  }
  finally {
    jobs.forEach { it.cancel() }
    jobs.forEach { it.join() }
  }
}

suspend fun <T> withSupervisor(coroutineContext: CoroutineContext = EmptyCoroutineContext,
                               body: suspend CoroutineScope.(scope: CoroutineScope) -> T): T {
  require(coroutineContext[Job] == null) { "Don't pass job to supervisor" }
  val context = currentCoroutineContext() + coroutineContext
  val supervisorJob = SupervisorJob(context.job)
  return try {
    coroutineScope {
      body(CoroutineScope(context + supervisorJob))
    }
  }
  finally {
    supervisorJob.cancelAndJoin()
  }
}

suspend fun <T> withCoroutineScope(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  body: suspend CoroutineScope.(scope: CoroutineScope) -> T,
): T {
  val context = currentCoroutineContext()
  val job = Job(context.job)
  return try {
    coroutineScope { body(CoroutineScope(context + coroutineContext + job)) }
  }
  finally {
    job.cancelAndJoin()
  }
}

/**
 * Deferred is also implementing Job interface,
 * but sometimes we are not interested in the wrapped value,
 * so it might be a good idea to not keep the reference
 */
fun <T> Deferred<T>.toJob(): Job {
  val job = Job()
  this.invokeOnCompletion { cause ->
    if (cause != null) {
      job.completeExceptionally(cause)
    } else {
      job.complete()
    }
  }
  return job
}