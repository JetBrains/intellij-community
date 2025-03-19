// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import kotlinx.coroutines.*

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

suspend fun <T> withSupervisor(body: suspend CoroutineScope.(scope: CoroutineScope) -> T): T {
  val context = currentCoroutineContext()
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

suspend fun<T> withCoroutineScope(body: suspend CoroutineScope.(scope: CoroutineScope) -> T): T {
  val context = currentCoroutineContext()
  val job = Job(context.job)
  return try {
    coroutineScope { body(CoroutineScope(context + job)) }
  }
  finally {
    job.cancelAndJoin()
  }
}