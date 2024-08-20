// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.Hashing
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus.Internal

private const val MAX_POWER_OF_TWO = 1 shl Integer.SIZE - 2
private const val ALL_SET = 0.inv()

@Internal
class StripedMutex(stripeCount: Int = 64) {
  private val mask = if (stripeCount > MAX_POWER_OF_TWO) ALL_SET else (1 shl (Integer.SIZE - Integer.numberOfLeadingZeros(stripeCount - 1))) - 1
  private val locks = Array(mask + 1) { Mutex() }

  fun getLock(string: String): Mutex {
    return locks[Hashing.komihash5_0().hashCharsToInt(string) and mask]
  }
}