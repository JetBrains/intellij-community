// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.time.Duration.Companion.seconds

class ConcurrencyTest {
  companion object {
    private val forEachConcurrentMethod: Method = Class.forName("org.jetbrains.intellij.build.ConcurrencyKt").getDeclaredMethod(
      "forEachConcurrent",
      Collection::class.java,
      Int::class.javaPrimitiveType,
      CoroutineDispatcher::class.java,
      Function2::class.java,
      Continuation::class.java,
    )
  }

  @Test
  fun workerDispatcherPreventsBlockingWorkersFromOccupyingCallerDispatcher() {
    val workerStarted = CompletableDeferred<Unit>()
    val siblingStarted = CompletableDeferred<Unit>()
    val latch = CountDownLatch(1)

    runBlocking {
      withContext(Dispatchers.Default.limitedParallelism(1)) {
        val work = launch {
          invokeForEachConcurrent(listOf(1), concurrency = 1, workerDispatcher = Dispatchers.IO) {
            workerStarted.complete(Unit)
            latch.await()
          }
        }

        workerStarted.await()

        val sibling = launch {
          siblingStarted.complete(Unit)
        }

        withTimeout(5.seconds) {
          siblingStarted.await()
        }

        latch.countDown()
        work.join()
        sibling.join()
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <T> invokeForEachConcurrent(
    items: Collection<T>,
    concurrency: Int,
    workerDispatcher: CoroutineDispatcher?,
    action: suspend (T) -> Unit,
  ) {
    suspendCancellableCoroutine { continuation ->
      try {
        val result = forEachConcurrentMethod.invoke(
          null,
          items,
          concurrency,
          workerDispatcher,
          action as Function2<T, Continuation<Unit>, Any?>,
          continuation,
        )
        if (result !== COROUTINE_SUSPENDED) {
          continuation.resume(Unit)
        }
      }
      catch (e: InvocationTargetException) {
        continuation.resumeWithException(e.targetException)
      }
      catch (e: Throwable) {
        continuation.resumeWithException(e)
      }
    }
  }
}
