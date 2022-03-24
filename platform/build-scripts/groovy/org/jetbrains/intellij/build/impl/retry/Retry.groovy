// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.retry

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages

import java.util.concurrent.TimeUnit
import java.util.function.IntFunction

@CompileStatic
final class Retry {
  private final int retries
  private final long delayMs
  private final BuildMessages log

  Retry(BuildMessages log, int retries = 10, long delayMs = 1000) {
    this.log = log
    this.delayMs = delayMs
    this.retries = retries
    if (retries < 1) {
      log.error("Retries number is lesser than one: $retries")
    }
  }

  def <T> T call(IntFunction<T> operation) {
    long delayMs = delayMs
    for (i in 1..retries) {
      try {
        return operation.apply(i)
      }
      catch (StopTrying e) {
        throw e.cause
      }
      catch (Exception e) {
        if (i == retries) {
          throw new RuntimeException("Failed all $retries attempts, see nested exception for details", e)
        }
        delayMs = backOff(delayMs, i, e)
      }
    }
    throw new RuntimeException("Should not be reached")
  }

  private static long backOffLimitMs = TimeUnit.MINUTES.toMillis(15)
  private static int backOffFactor = 2
  private static double backOffJitter = 0.1
  private final Random random = new Random()

  private long backOff(long delayMs, int attempt, Exception e) {
    def rawDelay = Math.min(delayMs, backOffLimitMs)
    def jitter = (random.nextGaussian() * rawDelay * backOffJitter).toLong()

    def effectiveDelay = rawDelay + jitter
    if (effectiveDelay > 0) {
      log.info("Attempt $attempt of $retries failed with '$e.message'. Retrying in ${effectiveDelay}ms")
      Thread.sleep(effectiveDelay)
    }
    return effectiveDelay * backOffFactor
  }
}
