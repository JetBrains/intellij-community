// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Every element is associated with a _set_ of keys and has an identity. A resolution map is a mapping of `key -> element`.
 * We would like to build a resolution map for a bunch of elements.
 * We can do it trivially unless there are conflicts: if two elements `e1` and `e2` are both associated with a key `k`, how should the mapping behave?
 * We forbid such situations and instead allow selecting which one of those conflicting elements should prevail,
 * and thus the other one is eliminated from the resolution altogether.
 *
 * If an element's key set contains duplicate keys, this is treated as a conflict (the element conflicts with itself),
 * and `resolveConflict` will be called with the same element instance for both `existing` and `candidate`.
 * In this case, `resolveConflict` **must** return `null`, otherwise an exception is thrown.
 *
 * NB: In case of conflicts, the result may depend on the order of additions.
 */
@ApiStatus.Internal
@VisibleForTesting
class ResolutionMapBuilder<K : Any, E : Any>(
  /**
   * Sequence must be idempotent
   */
  private val getKeys: (E) -> Sequence<K>,
  initialCapacity: Int = 2048,
  /**
   * @return one of the provided elements that should be left in the resolution map (the other one gets removed). if `null`, both are removed.
   *         **MUST** return `null` when `existing === candidate` (duplicate keys in element).
   */
  private val resolveConflict: (existing: E, candidate: E, key: K) -> E?,
) {
  private val map = HashMap<K, E>(initialCapacity)

  private fun dropAssociations(element: E, firstKeysCount: Int = Integer.MAX_VALUE) {
    for (key in getKeys(element).take(firstKeysCount)) {
      check(map[key] === element) { "$element -> $key, but ${map[key]} -> $key" }
      map.remove(key)
    }
  }

  /**
   * @return true if `element` was added to the resolution map
   */
  fun add(element: E): Boolean {
    for ((i, key) in getKeys(element).withIndex()) {
      val existing = map[key]
      if (existing == null) {
        map[key] = element
        continue
      }
      val survivor = resolveConflict(existing, element, key)
      
      // Enforce contract: duplicate keys (self-conflict) must return null
      if (existing === element && survivor != null) {
        throw IllegalStateException(
          "resolveConflict must return null when existing === candidate (duplicate key '$key' in element $element), but returned $survivor"
        )
      }
      
      when {
        survivor == null -> {
          dropAssociations(element, i)
          if (existing !== element) {
            dropAssociations(existing)
          }
          return false
        }
        survivor === existing -> {
          dropAssociations(element, i)
          return false
        }
        survivor === element -> {
          dropAssociations(existing)
          map[key] = element
        }
        else -> throw IllegalStateException("resolveConflict($existing, $element) -> $survivor")
      }
    }
    return true
  }

  /**
   * @return `true` if all keys of the `element` were deregistered, and `false` if none of the keys were registered;
   *  throws otherwise
   */
  fun remove(element: E): Boolean {
    val key = getKeys(element).first()
    val existing = map[key]
    if (existing == null) {
      for (key in getKeys(element)) {
        check(map[key] == null) { "$element -> $key, but ${map[key]} -> $key" }
      }
      return false
    }
    dropAssociations(element)
    return true
  }

  fun build(): Map<K, E> = HashMap(map)
}