// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import gnu.trove.TIntLongHashMap

class EventsIdentityThrottle(private val maxSize: Int, private val timeout: Long) {

  private val identities: TIntLongHashMap = TIntLongHashMap(maxSize)
  private var oldestIdentity: Int = 0
  private var oldestTimestamp: Long = 0

  fun tryPass(identity: Int, now: Long): Boolean {

    clearTimestamps(now)

    if (identities.containsKey(identity)) {
      return false
    }
    else {
      if (identities.size() == maxSize) {
        clearOldest()
      }

      identities.put(identity, now)

      return true
    }
  }

  private fun clearTimestamps(now: Long) {

    if (identities.isEmpty) {
      return
    }

    if (isObsolete(oldestTimestamp, now)) {

      oldestTimestamp = Long.MAX_VALUE

      for (identity in identities.keys()) {
        val timestamp = identities.get(identity)
        if (isObsolete(timestamp, now)) {
          identities.remove(identity)
        }
        else if (timestamp <= oldestTimestamp) {
          oldestIdentity = identity
          oldestTimestamp = timestamp
        }
      }
    }
  }

  private fun clearOldest() {

    if (identities.isEmpty) {
      return
    }

    identities.remove(oldestIdentity)

    oldestTimestamp = Long.MAX_VALUE

    identities.forEachEntry { identity, timestamp ->
      if (timestamp <= oldestTimestamp) {
        oldestIdentity = identity
        oldestTimestamp = timestamp
      }

      true
    }
  }

  private fun isObsolete(timestamp: Long, now: Long): Boolean = timestamp + timeout <= now
}