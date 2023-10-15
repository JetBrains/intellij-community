// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.util.containers.HashingStrategy

object HashingUtil {
  fun <T, K> mappingStrategy(
    keyExtractor: (T) -> K, keyStrategy: HashingStrategy<K> = HashingStrategy.canonical()
  ): HashingStrategy<T> = MappingHashingStrategy(keyStrategy, keyExtractor)
}

private class MappingHashingStrategy<T, K>(private val keyStrategy: HashingStrategy<K>, private val keyExtractor: (T) -> K)
  : HashingStrategy<T> {
  override fun hashCode(value: T?): Int = keyStrategy.hashCode(value?.let { keyExtractor(it) })

  override fun equals(o1: T?, o2: T?): Boolean {
    val mapped1 = o1?.let { keyExtractor(it) }
    val mapped2 = o2?.let { keyExtractor(it) }
    return keyStrategy.equals(mapped1, mapped2)
  }
}