// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import gnu.trove.TIntLongHashMap

class EventsIdentityThrottle(private val maxSize: Int, private val timeout: Long) {

  private val identities: TIntLongHashMap = TIntLongHashMap(maxSize)

  private var oldestIdentityCache: Int = 0
  private var oldestTimestampCache: Long = Long.MAX_VALUE

  fun tryPass(identity: Int, now: Long): Boolean {

    clearObsolete(now)

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

  fun size(now: Long): Int {
    clearObsolete(now)
    return identities.size()
  }

  fun getOldest(now: Long): Int? {
    clearObsolete(now)
    return withOldest { oldestIdentity, _ -> oldestIdentity }
  }

  private fun put(identity: Int, now: Long) {
    identities.put(identity, now)
    takeIntoAccountInOldest(identity, now)
  }

  private fun clearObsolete(now: Long) {
    withOldest { _, oldestTimestamp ->
      if (isObsolete(oldestTimestamp, now)) {
        doClearObsolete(now)
      }
    }
  }

  private fun clearOldest() {
    withOldest { oldestIdentity, _ ->
      identities.remove(oldestIdentity)
      updateOldest()
    }
  }

  private fun doClearObsolete(now: Long) {

    for (identity in identities.keys()) {
      if (isObsolete(identities.get(identity), now)) {
        identities.remove(identity)
      }
    }

    updateOldest()
  }

  private fun updateOldest() {
    oldestTimestampCache = Long.MAX_VALUE

    identities.forEachEntry { identity, timestamp ->
      takeIntoAccountInOldest(identity, timestamp)
      true
    }
  }

  private fun takeIntoAccountInOldest(identity: Int, timestamp: Long) {
    if (timestamp <= oldestTimestampCache) {
      oldestIdentityCache = identity
      oldestTimestampCache = timestamp
    }
  }

  private inline fun <T> withOldest(identityAndTimestamp: (Int, Long) -> T): T? {
    if (oldestTimestampCache != Long.MAX_VALUE) {
      return identityAndTimestamp(oldestIdentityCache, oldestTimestampCache)
    }
    return null
  }

  private fun isObsolete(timestamp: Long, now: Long): Boolean = timestamp + timeout <= now
}