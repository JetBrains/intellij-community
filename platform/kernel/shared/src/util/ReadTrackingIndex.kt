// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.jetbrains.rhizomedb.Datom
import com.jetbrains.rhizomedb.Pattern
import com.jetbrains.rhizomedb.ReadTrackingContext
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntConsumer
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.collections.set

internal class ReadTrackingIndex : ReadTrackingContext {
  companion object {
    private val EMPTY = LongOpenHashSet()
  }
  class LambdaInfo(val lambdaId: Int,
                   val update: () -> Unit) {
    var patterns: LongOpenHashSet? = null
    fun witness(pattern: Pattern) {
      when (val ps = patterns) {
        null -> patterns = LongOpenHashSet.of(pattern.hash)
        else -> ps.add(pattern.hash)
      }
    }
  }

  private var current: LambdaInfo? = null

  private val patternToKeys: Long2ObjectOpenHashMap<IntOpenHashSet> = Long2ObjectOpenHashMap()
  private val keyToPatterns: Int2ObjectOpenHashMap<LambdaInfo> = Int2ObjectOpenHashMap()

  override fun witness(pattern: Pattern) {
    current?.witness(pattern)
  }

  fun isEmpty(): Boolean {
    assert(keyToPatterns.isEmpty() == patternToKeys.isEmpty()) { "index is inconsistent" }
    return keyToPatterns.isEmpty()
  }

  fun updateIndex(lambdaInfo: LambdaInfo) {
    val key = lambdaInfo.lambdaId
    val newPatterns = lambdaInfo.patterns ?: EMPTY
    val patternToKeys = this.patternToKeys
    val keyToPatterns = this.keyToPatterns

    val oldFrameInfo: LambdaInfo? = keyToPatterns.remove(key)
    val oldPatterns = oldFrameInfo?.patterns
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
            val keys1 = IntOpenHashSet()
            keys1.add(key)
            patternToKeys[newP] = keys1
          }
          else {
            keys.add(key)
          }
        }
      }
    }

    if (!newPatterns.isEmpty()) {
      keyToPatterns[key] = lambdaInfo
    }
  }

  fun forget(key: Int) {
    val keyToPatterns = this.keyToPatterns
    val patternToKeys = this.patternToKeys
    keyToPatterns.remove(key)?.patterns?.iterator()?.let { iter ->
      while (iter.hasNext()) {
        val p = iter.nextLong()
        patternToKeys.get(p)?.let { keys ->
          when (keys.size) {
            1 -> {
              patternToKeys.remove(p)
            }
            else -> {
              keys.remove(key)
            }
          }
        }
      }
    }
  }

  fun query(novelty: Iterable<Datom>): List<LambdaInfo> {
    val patternToKeys = patternToKeys
    val result = ArrayList<LambdaInfo>()
    for (datom in novelty) {
      val patternHashes = Pattern.patternHashes(datom.eid, datom.attr, datom.value)
      for (hash in patternHashes) {
        patternToKeys.get(hash)?.let { keys ->
          keys.forEach(IntConsumer { key ->
            result.add(keyToPatterns[key])
          })
        }
      }
    }
    return result
  }

  fun runLambda(li: LambdaInfo) {
    val newLi = LambdaInfo(li.lambdaId, li.update)
    current = newLi
    newLi.update()
    updateIndex(newLi)
    current = null
  }
}