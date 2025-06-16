// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.Hashing
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class StripedMutex(stripeCount: Int = 64) {
  private val locks = Array(stripeCount) { Mutex() }
  private val mask = (stripeCount - 1).toLong()

  init {
    require(stripeCount > 0) { "Stripe count must be positive" }
    require(stripeCount and (stripeCount - 1) == 0) { "Stripe count must be a power of 2" }
  }

  fun getLock(string: String): Mutex {
    return locks[(Hashing.xxh3_64().hashBytesToLong(string.toByteArray()) and mask).toInt()]
  }

  fun getLockByHash(hash: Long): Mutex {
    return locks[(hash and mask).toInt()]
  }
}