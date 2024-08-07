// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.PlatformTestUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.function.Executable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future

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
    fun parallel(configure: Parallel.() -> Unit) {
      val pool = Parallel()
      pool.configure()
      pool.start.countDown()

      invokeAndWaitIfNeeded {
        Assertions.assertAll("Execute tasks in parallel", pool.futures.map { future ->
          Executable {
            PlatformTestUtil.waitForFuture(future)
          }
        })
      }
    }
  }
}