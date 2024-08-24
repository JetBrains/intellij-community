// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Allows mapping a collection of items [T] to scoped (coroutine scope bound) values [V]
 * An intermittent key [K] is used to uniquely identify items
 *
 * @param cs parent scope for value scopes
 * @param keyExtractor should be a quick-to-run function extracting a key from item
 * @param hashingStrategy strategy used to compare keys
 * @param mapper factory function to create a value from item
 * @param destroy destructor function to destroy a value
 * @param update function used to update value if a new item is supplied for the existing key
 */
@ApiStatus.Internal
class MappingScopedItemsContainer<T, K, V> internal constructor(
  private val cs: CoroutineScope,
  private val keyExtractor: (T) -> K,
  private val hashingStrategy: HashingStrategy<K>,
  private val mapper: CoroutineScope.(T) -> V,
  private val destroy: suspend V.() -> Unit,
  private val update: (suspend V.(T) -> Unit)? = null
) {
  private val _mappingState = MutableStateFlow<Map<K, ScopingWrapper<V>>>(emptyMap())
  val mappingState: StateFlow<Map<K, V>> = _mappingState.mapState { it.mapValues { (_, value) -> value.value } }
  private val mapGuard = Mutex()

  suspend fun update(items: Iterable<T>) = mapGuard.withLock {
    withContext(NonCancellable) {
      val currentMap = _mappingState.value
      var hasStructureChanges = false
      val newItemsSet = CollectionFactory.createLinkedCustomHashingStrategySet(hashingStrategy).also {
        items.mapTo(it, keyExtractor)
      }

      val result = createLinkedMap<K, ScopingWrapper<V>>(hashingStrategy)
      // destroy missing
      for ((key, scopedValue) in currentMap) {
        if (!newItemsSet.contains(key)) {
          hasStructureChanges = true
          scopedValue.value.destroy()
          scopedValue.cancel()
        }
        else {
          result[key] = scopedValue
        }
      }

      // add new or update existing
      for (item in items) {
        val itemKey = keyExtractor(item)
        val existing = result[itemKey]
        if (existing == null) {
          val valueScope = cs.childScope()
          result[itemKey] = ScopingWrapper(valueScope, mapper(valueScope, item))
          hasStructureChanges = true
        }
        else {
          // if not inferring nullability fsr
          update?.let { existing.value.it(item) }
          result[itemKey] = existing
        }
      }

      if (hasStructureChanges) {
        _mappingState.value = result
      }
    }
  }

  suspend fun addIfAbsent(item: T): V = mapGuard.withLock {
    withContext(NonCancellable) {
      val key = keyExtractor(item)
      _mappingState.value[key]?.value ?: _mappingState.updateAndGet {
        val valueScope = cs.childScope()
        val newValue = ScopingWrapper(valueScope, mapper(valueScope, item))
        it + (key to newValue)
      }[key]!!.value
    }
  }

  companion object {
    fun <T, V> byIdentity(cs: CoroutineScope, mapper: CoroutineScope.(T) -> V) =
      MappingScopedItemsContainer(cs, { it }, HashingStrategy.identity(), mapper, {})

    fun <T, V> byEquality(cs: CoroutineScope, mapper: CoroutineScope.(T) -> V) =
      MappingScopedItemsContainer(cs, { it }, HashingStrategy.canonical(), mapper, {})
  }
}

private fun <T, R> createLinkedMap(hashingStrategy: HashingStrategy<T>): MutableMap<T, R> =
  CollectionFactory.createLinkedCustomHashingStrategyMap(hashingStrategy)

private data class ScopingWrapper<T>(val scope: CoroutineScope, val value: T) {
  suspend fun cancel() = scope.cancelAndJoinSilently()
}