// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Pattern
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongSet

internal class PatternIndex<T> {
  private val keyToPatterns = HashMap<T, LongSet>()
  private val patternToKeys = Long2ObjectOpenHashMap<MutableSet<T>>()

  fun get(eid: EID, attr: Attribute<*>, value: Any): Iterable<T> {
    val result = ArrayList<T>()
    val patternHashes = Pattern.patternHashes(eid, attr, value)
    for (hash in patternHashes) {
      patternToKeys.get(hash)?.let { keys -> result.addAll(keys) }
    }
    return result
  }

  fun update(key: T, newPatterns: LongSet) {
    val oldPatterns = keyToPatterns.remove(key)
    oldPatterns?.iterator()?.let { iter ->
      while (iter.hasNext()) {
        val oldP = iter.nextLong()
        val keys = patternToKeys.get(oldP)
        if (keys != null && !newPatterns.contains(oldP)) {
          keys.remove(key)
          if (keys.isEmpty()) {
            patternToKeys.remove(oldP)
          }
        }
      }
    }

    newPatterns.iterator().let { iter ->
      while (iter.hasNext()) {
        val newP = iter.nextLong()
        if (oldPatterns == null || !oldPatterns.contains(newP)) {
          val keys = patternToKeys.get(newP)
          if (keys == null) {
            patternToKeys[newP] = adaptiveSetOf(key)
          }
          else {
            keys.add(key)
          }
        }
      }
    }

    if (!newPatterns.isEmpty()) {
      keyToPatterns[key] = newPatterns
    }
  }
}