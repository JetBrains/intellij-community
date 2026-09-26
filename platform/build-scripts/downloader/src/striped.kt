// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.locks.ReentrantLock

/**
 * Serializes actions by hash stripe. Waiting for a stripe is interruptible, and an action can acquire its stripe again.
 */
@Internal
class StripedLock(stripeCount: Int = 64) {
  private val locks = Array(stripeCount) { ReentrantLock() }
  private val mask = (stripeCount - 1).toLong()

  init {
    require(stripeCount > 0) { "Stripe count must be positive" }
    require(stripeCount and (stripeCount - 1) == 0) { "Stripe count must be a power of 2" }
  }

  fun <T> withLock(key: String, action: () -> T): T {
    return withLockByHash(Hashing.xxh3_64().hashBytesToLong(key.toByteArray()), action)
  }

  fun <T> withLockByHash(hash: Long, action: () -> T): T {
    val lock = locks[(hash and mask).toInt()]
    lock.lockInterruptibly()
    try {
      return action()
    }
    finally {
      lock.unlock()
    }
  }
}
