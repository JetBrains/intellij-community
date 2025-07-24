// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual


fun <K, V> ConcurrentHashMap(): ConcurrentHashMap<K, V> = linkToActual()

interface ConcurrentHashMap<K, V>: MutableMap<K, V> {
  fun putIfAbsent(key: K, value: V): V?

  fun computeIfAbsent(key: K, f: (K) -> V): V

  fun computeIfPresent(key: K, f: (K, V) -> V): V?

  fun compute(key: K, f: (K, V?) -> V?): V?

  fun remove(key: K, value: V): Boolean
}

