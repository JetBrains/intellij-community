// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

import kotlinx.coroutines.*

internal fun CoroutineScope.childSupervisorScope(): CoroutineScope = this + childSupervisorJob()
internal fun CoroutineScope.childSupervisorJob(): CompletableJob = SupervisorJob(coroutineContext[Job]!!)

@Suppress("EXPERIMENTAL_API_USAGE")
fun <T> Deferred<T>.blockingGet(): T =
  if (isCompleted) {  // fast path
    getCompleted()
  }
  else {
    runBlocking(Dispatchers.Unconfined) {
      await()
    }
  }