// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

class Throttle(
  private val amount: Int,
  private val perMillis: Long
) {
  private val timestamps = LongArray(amount)

  private var currentSize = 0
  private var lastIndex = 0

  /**
   * @param now is supposed to not decrease between calls
   */
  fun tryPass(now: Long): Boolean {
    clearTimestamps(now)

    if (currentSize < amount) {
      addTimestamp(now)
      return true
    }

    return false
  }

  private fun addTimestamp(now: Long) {
    lastIndex = (lastIndex + 1) % amount
    timestamps[lastIndex] = now
    if (currentSize < amount) currentSize += 1
  }

  private fun clearTimestamps(now: Long) {
    var headIndex = (amount + lastIndex + 1 - currentSize) % amount

    while (currentSize > 0 && timestamps[headIndex] + perMillis <= now) {
      headIndex = (headIndex + 1) % amount
      currentSize -= 1
    }
  }
}