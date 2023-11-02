// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.containers.ConcurrentLongObjectHashMap
import com.intellij.util.containers.ConcurrentLongObjectMap
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
// implementation of `copyOf` is allowed to not do copy - it can return the same map, read `copyOf` as `immutable`
interface Java11Shim {
  companion object {
    var INSTANCE: Java11Shim = object : Java11Shim {
      override fun <K, V> copyOf(map: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(map)

      override fun <E> copyOf(collection: Collection<E>): Set<E> = Collections.unmodifiableSet(HashSet(collection))

      override fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V> {
        return ConcurrentLongObjectHashMap()
      }
    }
  }

  fun <K, V : Any?> copyOf(map: Map<K, V>): Map<K, V>

  fun <E> copyOf(collection: Collection<E>): Set<E>

  fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V>
}
