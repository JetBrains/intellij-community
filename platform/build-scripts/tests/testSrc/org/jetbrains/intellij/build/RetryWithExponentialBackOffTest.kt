// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class RetryWithExponentialBackOffTest {
  @Test
  fun test() {
    class AttemptFailure : Exception()
    runBlocking {
      val attempts = 5
      var actualAttempts = 0
      lateinit var exception: Exception
      try {
        retryWithExponentialBackOff(
          attempts = attempts,
          initialDelayMs = 0,
          backOffFactor = 0,
          backOffJitter = 0.0,
        ) {
          actualAttempts++
          throw AttemptFailure()
        }
      }
      catch (e: Exception) {
        exception = e
      }
      assert(actualAttempts == attempts) {
        "Expected $attempts attempts, but performed $actualAttempts"
      }
      val exceptions = sequenceOf(exception) + exception.suppressedExceptions
      assert(exceptions.count { it is AttemptFailure } == attempts) {
        "Expected $attempts exceptions, but only ${exceptions.count { it is AttemptFailure }} thrown"
      }
    }
  }
}