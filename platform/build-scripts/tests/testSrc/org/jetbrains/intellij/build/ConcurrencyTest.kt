// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.time.Duration.Companion.milliseconds
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

    private val mapConcurrentMethod: Method = Class
      .forName("org.jetbrains.intellij.build.ConcurrentCollectionsKt")
      .getDeclaredMethod(
        "mapConcurrent",
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

  @Test
  fun mapConcurrentPreservesInputOrder() {
    runBlocking {
      val result = invokeMapConcurrent(listOf(1, 2, 3), concurrency = 3) { value ->
        delay(((4 - value) * 10).milliseconds)
        value
      }

      assertThat(result).containsExactly(1, 2, 3)
    }
  }

  @Test
  fun mapConcurrentValidatesConcurrency() {
    assertThatThrownBy {
      runBlocking {
        invokeMapConcurrent(listOf(1), concurrency = 0) { it }
      }
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Concurrency must be positive")
  }

  @Test
  fun mapConcurrentPropagatesTransformFailures() {
    assertThatThrownBy {
      runBlocking {
        invokeMapConcurrent(listOf(1, 2, 3), concurrency = 2) { item ->
          check(item != 2) { "boom" }
          item
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("boom")
  }

  @Test
  fun mapConcurrentPropagatesCancellation() {
    assertThatThrownBy {
      runBlocking {
        invokeMapConcurrent(listOf(1, 2, 3), concurrency = 2) { item ->
          if (item == 2) {
            throw CancellationException("cancel")
          }
          delay(50.milliseconds)
          item
        }
      }
    }
      .isInstanceOf(CancellationException::class.java)
      .hasMessageContaining("cancel")
  }

  @Test
  fun mapConcurrentUsesIoWorkers() {
    val workerStarted = CompletableDeferred<Unit>()
    val siblingStarted = CompletableDeferred<Unit>()
    val latch = CountDownLatch(1)

    runBlocking {
      withContext(Dispatchers.Default.limitedParallelism(1)) {
        val work = launch {
          invokeMapConcurrent(listOf(1), concurrency = 1, workerDispatcher = Dispatchers.IO) {
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

  @Suppress("UNCHECKED_CAST")
  private suspend fun <T, R> invokeMapConcurrent(
    items: Collection<T>,
    concurrency: Int,
    workerDispatcher: CoroutineDispatcher? = null,
    action: suspend (T) -> R,
  ): List<R> {
    return suspendCancellableCoroutine { continuation ->
      try {
        val result = mapConcurrentMethod.invoke(
          null,
          items,
          concurrency,
          workerDispatcher,
          action as Function2<T, Continuation<R>, Any?>,
          continuation,
        )
        if (result !== COROUTINE_SUSPENDED) {
          continuation.resume(result as List<R>)
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
