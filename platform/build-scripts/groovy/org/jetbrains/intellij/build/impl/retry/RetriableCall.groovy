// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.retry

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages

import java.util.concurrent.TimeUnit

@CompileStatic
class RetriableCall {
  private final int retries
  private final long delayMs
  private final BuildMessages log

  RetriableCall(BuildMessages log, int retries = 10, long delayMs = 1000) {
    this.log = log
    this.delayMs = delayMs
    this.retries = retries
  }

  def <T> T retry(Closure<T> operation) {
    def delayMs = delayMs
    for (i in 1..retries) {
      try {
        return operation(i)
      }
      catch (Exception e) {
        if (i == retries) {
          log.error("Failed all $retries attempts, see nested exception for details", e)
        }
        if (i > 1) delayMs = backOff(delayMs, e)
        log.info("Retry $i of $retries failed with '$e.message'. Retrying in ${delayMs}ms")
        if (delayMs > 0) Thread.sleep(delayMs)
      }
    }
    log.error("Should not be reached") as T
  }

  private long backOffLimitInMs = TimeUnit.MINUTES.toMillis(15)
  private int backOffFactor = 2
  private double backOffJitter = 0.1
  private Random random = new Random()

  private long backOff(long delayMs, Exception cause) {
    long nextDelay = Math.min(delayMs * backOffFactor, backOffLimitInMs)
    if (delayMs > backOffLimitInMs) {
      log.error("Back off limit ${backOffLimitInMs}ms exceeded, see nested exception for details", cause)
    }
    nextDelay += (random.nextGaussian() * delayMs * backOffJitter).toLong()
    return nextDelay
  }
}
