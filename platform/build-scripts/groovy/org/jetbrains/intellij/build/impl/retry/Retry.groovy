// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.retry

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages

import java.util.concurrent.TimeUnit

@CompileStatic
class Retry {
  private final int retries
  private final long delayMs
  private final BuildMessages log

  Retry(BuildMessages log, int retries = 10, long delayMs = 1000) {
    this.log = log
    this.delayMs = delayMs
    this.retries = retries
  }

  def <T> T call(Closure<T> operation) {
    def delayMs = delayMs
    for (i in 1..retries) {
      try {
        return operation(i)
      }
      catch (StopTrying e) {
        throw e.cause
      }
      catch (Exception e) {
        if (i == retries) {
          throw new RuntimeException("Failed all $retries attempts, see nested exception for details", e)
        }
        if (i > 1) delayMs = backOff(delayMs, i, e)
      }
    }
    throw new RuntimeException("Should not be reached")
  }

  private static long backOffLimitMs = TimeUnit.MINUTES.toMillis(15)
  private static int backOffFactor = 2
  private static double backOffJitter = 0.1
  private Random random = new Random()

  private long backOff(long delayMs, int attempt, Exception e) {
    delayMs = Math.min(delayMs, backOffLimitMs)
    def nextDelay = Math.min(delayMs * backOffFactor, backOffLimitMs) +
                    (random.nextGaussian() * delayMs * backOffJitter).toLong()
    if (nextDelay > 0) {
      log.info("Attempt $attempt of $retries failed with '$e.message'. Retrying in ${nextDelay}ms")
      Thread.sleep(nextDelay)
    }
    return nextDelay
  }
}
