// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkntValidationCancellationTest : BasePlatformTestCase() {

  fun `test progress cancellation interrupts validation action`() {
    @Suppress("DEPRECATION") // Tests the legacy ProgressIndicator-to-coroutine cancellation bridge.
    val indicator = EmptyProgressIndicator()
    val actionStarted = CountDownLatch(1)
    val action = CountDownLatch(1)
    val executor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "networknt-validation-cancellation-test")
    }

    try {
      val validation = executor.submit {
        ProgressManager.getInstance().runProcess(
          {
            NetworkntValidationService(project).runInterruptibleValidation {
              actionStarted.countDown()
              action.await()
            }
          }, indicator)
      }

      assertTrue("Validation action did not start", actionStarted.await(5, TimeUnit.SECONDS))
      indicator.cancel()

      val error = runCatching { validation.get(5, TimeUnit.SECONDS) }.exceptionOrNull()
      assertTrue("Expected validation cancellation, got $error", error is ExecutionException)
      val cause = (error as ExecutionException).cause
      assertTrue(
        "Expected validation cancellation, got ${cause?.javaClass?.name}: ${cause?.message}",
        cause is ProcessCanceledException || cause is java.util.concurrent.CancellationException,
      )
    }
    finally {
      indicator.cancel()
      executor.shutdownNow()
      assertTrue("Validation executor did not stop", executor.awaitTermination(5, TimeUnit.SECONDS))
    }
  }
}
