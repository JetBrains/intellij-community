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

      put(identity, now)

      return true
    }
  }

  private fun put(identity: Int, now: Long) {
    if (oldIdentitiesAbsent() || isOldest(now)) {
      oldestIdentity = identity
      oldestTimestamp = now
    }

    identities.put(identity, now)
  }

  private fun clearTimestamps(now: Long) {
    if (oldIdentitiesAbsent()) {
      return
    }

    if (isObsolete(oldestTimestamp, now)) {

      oldestTimestamp = Long.MAX_VALUE

      for (identity in identities.keys()) {
        val timestamp = identities.get(identity)
        if (isObsolete(timestamp, now)) {
          identities.remove(identity)
        }
        else if (isOldest(timestamp)) {
          oldestIdentity = identity
          oldestTimestamp = timestamp
        }
      }
    }
  }

  private fun clearOldest() {
    if (oldIdentitiesAbsent()) {
      return
    }

    identities.remove(oldestIdentity)

    oldestTimestamp = Long.MAX_VALUE

    identities.forEachEntry { identity, timestamp ->
      if (isOldest(timestamp)) {
        oldestIdentity = identity
        oldestTimestamp = timestamp
      }

      true
    }
  }

  private fun isOldest(timestamp: Long): Boolean = timestamp <= oldestTimestamp

  private fun isObsolete(timestamp: Long, now: Long): Boolean = timestamp + timeout <= now

  private fun oldIdentitiesAbsent() = identities.isEmpty
}