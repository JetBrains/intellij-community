// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.function.Executable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class Parallel private constructor() {

  private val futures = ArrayList<Future<Nothing?>>()
  private val start = CountDownLatch(1)

  fun thread(block: () -> Unit) {
    val future = CompletableFuture<Nothing?>()
    futures.add(future)
    kotlin.concurrent.thread {
      start.await()
      try {
        block()
        future.complete(null)
      }
      catch (throwable: Throwable) {
        val exception = AssertionError(throwable)
        future.completeExceptionally(exception)
      }
    }
  }

  companion object {

    /**
     * At the same time starts threads and waits for their completion
     */
    fun parallel(
      timeout: Duration = DEFAULT_TEST_TIMEOUT,
      configure: Parallel.() -> Unit
    ) {
      val pool = Parallel()
      pool.configure()
      pool.start.countDown()

      Assertions.assertAll("Execute tasks in parallel", pool.futures.map { future ->
        Executable {
          future.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
      })
    }
  }
}