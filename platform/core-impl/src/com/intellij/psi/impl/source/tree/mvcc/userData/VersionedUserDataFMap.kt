// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc.userData

import com.intellij.openapi.util.Key
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap
import com.intellij.util.ArrayUtil
import com.intellij.util.keyFMap.ArrayBackedFMap
import com.intellij.util.keyFMap.EmptyFMap
import com.intellij.util.keyFMap.KeyFMap
import com.intellij.util.keyFMap.MapBackedFMap
import com.intellij.util.keyFMap.OneElementFMap
import com.intellij.util.keyFMap.PairElementsFMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Debug
import java.util.Arrays

/**
 * A [KeyFMap] that maps user data [Key] to versioned references ([VersionedPayloadMap]).
 *
 * The storage keeps every key that is known in any live version, while all [KeyFMap] operations
 * operate on the slice of the storage that is visible for the current PSI version.
 *
 * Optimized for space.
 *
 * Note that [equals]/[hashCode] compare the whole versioned storage rather than the currently visible slice,
 * which deliberately deviates from the [KeyFMap] contract of a [java.util.Map]-compatible hash code.
 *
 * Note also that [getValueIdentityHashCode] repeats the formula of [ArrayBackedFMap] for every flavor, i.e. it walks
 * the storage in the [Key.hashCode] order. It therefore matches the plain implementations up to [MapBackedFMap],
 * which walks its hash table in an unspecified order.
 */
@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "storageSize() != 0", childrenArray = "arrayOfEntries()")
@ApiStatus.Internal
sealed interface VersionedUserDataFMap : KeyFMap {
  companion object {
    @JvmStatic
    fun empty(): VersionedUserDataFMap = VersionedUserDataFMap0

    /**
     * Converts an arbitrary [map] to a versioned one by installing all its values into the current PSI version.
     */
    @JvmStatic
    fun from(map: KeyFMap): VersionedUserDataFMap {
      if (map is VersionedUserDataFMap) {
        return map
      }
      val version = InternalPsiVersioning.getCurrentPsiVersion()
      var result: VersionedUserDataFMap = empty()
      for (key in map.keys) {
        @Suppress("UNCHECKED_CAST")
        val value = map.get(key as Key<Any>) ?: continue
        val payloadMap = VersionedPayloadMap.empty().insert(version, value) ?: continue
        result = result.withPayloadMap(key, payloadMap)
      }
      // every payload map here holds a single version, so garbage collection is not needed
      return result
    }
  }

  /**
   * The number of keys in the versioned storage, including the ones that are invisible for the current version.
   */
  fun storageSize(): Int

  /**
   * The index of the key at [index], i.e. its [Key.hashCode]. Unlike [keyAt], it stays available after the key
   * itself has been garbage collected.
   */
  fun keyIndexAt(index: Int): Int

  /**
   * The key at [index], or `null` if the key has already been garbage collected.
   */
  fun keyAt(index: Int): Key<*>?

  fun payloadMapAt(index: Int): VersionedPayloadMap

  fun findPayloadMap(key: Key<*>): VersionedPayloadMap?

  /**
   * Returns a [KeyFMap] where [key] is associated with [payloadMap],
   * or where [key] is absent if [payloadMap] does not contain anything visible.
   *
   * Returns `this` if no modifications are needed. Does not run garbage collection.
   */
  fun withPayloadMap(key: Key<*>, payloadMap: VersionedPayloadMap?): VersionedUserDataFMap

  /**
   * Produces a map where obsolete versions are removed.
   *
   * Returns `this` if there is nothing to remove.
   */
  fun cleanup(minVersion: Long): VersionedUserDataFMap

  fun isStorageEmpty(): Boolean = storageSize() == 0

  @Suppress("unused") // used in `@Debug.Renderer`
  fun arrayOfEntries(): Array<VersionedUserDataEntry> {
    return Array(storageSize()) { VersionedUserDataEntry(keyAt(it), payloadMapAt(it)) }
  }
}

@ApiStatus.Internal
data class VersionedUserDataEntry(val key: Key<*>?, val payloadMap: VersionedPayloadMap)

/**
 * Space-optimized flavor of empty [VersionedUserDataFMap].
 */
private object VersionedUserDataFMap0 : EmptyFMap(), VersionedUserDataFMap {
  override fun storageSize(): Int = 0
  override fun keyIndexAt(index: Int): Int = throw IndexOutOfBoundsException(index.toString())
  override fun keyAt(index: Int): Key<*> = throw IndexOutOfBoundsException(index.toString())
  override fun payloadMapAt(index: Int): VersionedPayloadMap = throw IndexOutOfBoundsException(index.toString())
  override fun findPayloadMap(key: Key<*>): VersionedPayloadMap? = null
  override fun isStorageEmpty(): Boolean = true
  override fun cleanup(minVersion: Long): VersionedUserDataFMap = this

  override fun withPayloadMap(key: Key<*>, payloadMap: VersionedPayloadMap?): VersionedUserDataFMap {
    val visiblePayloadMap = payloadMap.normalize() ?: return this
    return VersionedUserDataFMap1(key, visiblePayloadMap)
  }

  // `get`, `size`, `isEmpty`, `getKeys`, `getValueIdentityHashCode` and `toString` of `EmptyFMap` already describe
  // an empty slice, so only the operations that have to produce a versioned map are overridden here
  override fun <V> plus(key: Key<V>, value: V & Any): KeyFMap = plusImpl(key, value)
  override fun minus(key: Key<*>): KeyFMap = minusImpl(key)
  override fun equalsByReference(other: KeyFMap): Boolean = visibleEqualsByReference(other)
}

/**
 * Space-optimized flavor of [VersionedUserDataFMap] that stores one key.
 */
private class VersionedUserDataFMap1(
  key: Key<*>,
  value: VersionedPayloadMap,
) : OneElementFMap(key, value), VersionedUserDataFMap {

  private val payloadMap: VersionedPayloadMap get() = myValue as VersionedPayloadMap

  override fun storageSize(): Int = 1
  override fun keyIndexAt(index: Int): Int = myKey.hashCode()
  override fun keyAt(index: Int): Key<*> = myKey
  override fun payloadMapAt(index: Int): VersionedPayloadMap = payloadMap
  override fun isStorageEmpty(): Boolean = false
  override fun findPayloadMap(key: Key<*>): VersionedPayloadMap? = if (key === myKey) payloadMap else null

  override fun withPayloadMap(key: Key<*>, payloadMap: VersionedPayloadMap?): VersionedUserDataFMap {
    val visiblePayloadMap = payloadMap.normalize()
    if (key === myKey) {
      return when {
        visiblePayloadMap == null -> VersionedUserDataFMap.empty()
        visiblePayloadMap === this.payloadMap -> this
        else -> VersionedUserDataFMap1(key, visiblePayloadMap)
      }
    }
    if (visiblePayloadMap == null) {
      return this
    }
    // `PairElementsFMap` orders the pair by `Key.hashCode()` on its own
    return VersionedUserDataFMap2(myKey, this.payloadMap, key, visiblePayloadMap)
  }

  override fun cleanup(minVersion: Long): VersionedUserDataFMap {
    val payloadMap = this.payloadMap
    val cleanedPayloadMap = payloadMap.cleaned(minVersion) ?: return VersionedUserDataFMap.empty()
    return if (cleanedPayloadMap === payloadMap) this else VersionedUserDataFMap1(myKey, cleanedPayloadMap)
  }

  override fun <V> plus(key: Key<V>, value: V & Any): KeyFMap = plusImpl(key, value)
  override fun minus(key: Key<*>): KeyFMap = minusImpl(key)
  override fun <V> get(key: Key<V>): V? = getImpl(key)
  override fun size(): Int = visibleSize()
  override fun isEmpty(): Boolean = isVisiblyEmpty()
  override fun getKeys(): Array<Key<*>> = visibleKeys()
  override fun getValueIdentityHashCode(): Int = visibleValueIdentityHashCode()
  override fun equalsByReference(other: KeyFMap): Boolean = visibleEqualsByReference(other)

  override fun toString(): String = currentSliceToString()
  override fun equals(other: Any?): Boolean = storageEquals(other)
  override fun hashCode(): Int = storageHashCode()
}

/**
 * Space-optimized flavor of [VersionedUserDataFMap] that stores two keys.
 */
private class VersionedUserDataFMap2(
  firstKey: Key<*>,
  firstValue: VersionedPayloadMap,
  secondKey: Key<*>,
  secondValue: VersionedPayloadMap,
  // `PairElementsFMap` maintains the `key1.hashCode() < key2.hashCode()` invariant, so the arguments may come in any order
) : PairElementsFMap(firstKey, firstValue, secondKey, secondValue), VersionedUserDataFMap {

  private val firstPayloadMap: VersionedPayloadMap get() = value1 as VersionedPayloadMap
  private val secondPayloadMap: VersionedPayloadMap get() = value2 as VersionedPayloadMap

  override fun storageSize(): Int = 2
  override fun keyIndexAt(index: Int): Int = if (index == 0) key1.hashCode() else key2.hashCode()
  override fun keyAt(index: Int): Key<*> = if (index == 0) key1 else key2
  override fun payloadMapAt(index: Int): VersionedPayloadMap = if (index == 0) firstPayloadMap else secondPayloadMap
  override fun isStorageEmpty(): Boolean = false

  override fun findPayloadMap(key: Key<*>): VersionedPayloadMap? = when {
    key === key1 -> firstPayloadMap
    key === key2 -> secondPayloadMap
    else -> null
  }

  override fun withPayloadMap(key: Key<*>, payloadMap: VersionedPayloadMap?): VersionedUserDataFMap {
    val visiblePayloadMap = payloadMap.normalize()
    if (key === key1) {
      return when {
        visiblePayloadMap == null -> VersionedUserDataFMap1(key2, secondPayloadMap)
        visiblePayloadMap === firstPayloadMap -> this
        else -> VersionedUserDataFMap2(key1, visiblePayloadMap, key2, secondPayloadMap)
      }
    }
    if (key === key2) {
      return when {
        visiblePayloadMap == null -> VersionedUserDataFMap1(key1, firstPayloadMap)
        visiblePayloadMap === secondPayloadMap -> this
        else -> VersionedUserDataFMap2(key1, firstPayloadMap, key2, visiblePayloadMap)
      }
    }
    if (visiblePayloadMap == null) {
      return this
    }
    val keyCode = key.hashCode()
    val keyCode1 = key1.hashCode()
    val keyCode2 = key2.hashCode()
    return when {
      keyCode < keyCode1 -> ArrayVersionedUserDataFMap(intArrayOf(keyCode, keyCode1, keyCode2),
                                                       arrayOf(visiblePayloadMap, firstPayloadMap, secondPayloadMap))
      keyCode < keyCode2 -> ArrayVersionedUserDataFMap(intArrayOf(keyCode1, keyCode, keyCode2),
                                                       arrayOf(firstPayloadMap, visiblePayloadMap, secondPayloadMap))
      else -> ArrayVersionedUserDataFMap(intArrayOf(keyCode1, keyCode2, keyCode),
                                         arrayOf(firstPayloadMap, secondPayloadMap, visiblePayloadMap))
    }
  }

  override fun cleanup(minVersion: Long): VersionedUserDataFMap {
    val payloadMap1 = firstPayloadMap
    val payloadMap2 = secondPayloadMap
    val cleaned1 = payloadMap1.cleaned(minVersion)
    val cleaned2 = payloadMap2.cleaned(minVersion)
    if (cleaned1 === payloadMap1 && cleaned2 === payloadMap2) {
      return this
    }
    return when {
      cleaned1 == null && cleaned2 == null -> VersionedUserDataFMap.empty()
      cleaned1 == null -> VersionedUserDataFMap1(key2, cleaned2!!)
      cleaned2 == null -> VersionedUserDataFMap1(key1, cleaned1)
      else -> VersionedUserDataFMap2(key1, cleaned1, key2, cleaned2)
    }
  }

  override fun <V> plus(key: Key<V>, value: V & Any): KeyFMap = plusImpl(key, value)
  override fun minus(key: Key<*>): KeyFMap = minusImpl(key)
  override fun <V> get(key: Key<V>): V? = getImpl(key)
  override fun size(): Int = visibleSize()
  override fun isEmpty(): Boolean = isVisiblyEmpty()
  override fun getKeys(): Array<Key<*>> = visibleKeys()
  override fun getValueIdentityHashCode(): Int = visibleValueIdentityHashCode()
  override fun equalsByReference(other: KeyFMap): Boolean = visibleEqualsByReference(other)

  override fun toString(): String = currentSliceToString()
  override fun equals(other: Any?): Boolean = storageEquals(other)
  override fun hashCode(): Int = storageHashCode()
}

/**
 * Implementation of [VersionedUserDataFMap] for a moderate number of keys.
 *
 * It inherits the layout of [ArrayBackedFMap]: the sorted `int[]` of key indices and the parallel array of values,
 * which here are the [VersionedPayloadMap] of the corresponding keys. Neither array is ever mutated in place,
 * so every operation produces a new instance.
 */
private class ArrayVersionedUserDataFMap(
  keyIndices: IntArray,
  payloadMaps: Array<Any>,
) : ArrayBackedFMap(keyIndices, payloadMaps), VersionedUserDataFMap {

  override fun storageSize(): Int = keys.size
  override fun keyIndexAt(index: Int): Int = keys[index]
  override fun keyAt(index: Int): Key<*>? = Key.getKeyByIndex<Any>(keys[index])
  override fun payloadMapAt(index: Int): VersionedPayloadMap = values[index] as VersionedPayloadMap
  override fun isStorageEmpty(): Boolean = false

  override fun findPayloadMap(key: Key<*>): VersionedPayloadMap? {
    val index = indexOfKey(key.hashCode())
    return if (index < 0) null else values[index] as VersionedPayloadMap
  }

  /**
   * The index of [keyCode] in [keys], or `-insertionPoint - 1` if it is absent. [keys] is sorted, so the search
   * can bail out as soon as a bigger key index is met.
   */
  private fun indexOfKey(keyCode: Int): Int {
    for (i in keys.indices) {
      val key = keys[i]
      if (key == keyCode) return i
      if (key > keyCode) return -i - 1
    }
    return -keys.size - 1
  }

  override fun withPayloadMap(key: Key<*>, payloadMap: VersionedPayloadMap?): VersionedUserDataFMap {
    val visiblePayloadMap = payloadMap.normalize()
    val keyCode = key.hashCode()
    val index = indexOfKey(keyCode)
    if (index >= 0) {
      if (visiblePayloadMap == null) {
        return withoutEntryAt(index)
      }
      if (values[index] === visiblePayloadMap) {
        return this
      }
      val newValues = values.clone()
      newValues[index] = visiblePayloadMap
      // `keys` is never mutated in place, so it can be reused
      return ArrayVersionedUserDataFMap(keys, newValues)
    }
    if (visiblePayloadMap == null) {
      return this
    }
    val insertionPoint = -index - 1
    if (keys.size < ArrayBackedFMap.ARRAY_THRESHOLD) {
      return ArrayVersionedUserDataFMap(ArrayUtil.insert(keys, insertionPoint, keyCode),
                                        ArrayUtil.insert(values, insertionPoint, visiblePayloadMap as Any))
    }
    return MapVersionedUserDataFMap(ArrayUtil.insert(keys, insertionPoint, keyCode),
                                    ArrayUtil.insert(values, insertionPoint, visiblePayloadMap as Any))
  }

  private fun withoutEntryAt(index: Int): VersionedUserDataFMap {
    if (keys.size == 3) {
      val firstIndex = if (index == 0) 1 else 0
      val secondIndex = if (index == 2) 1 else 2
      val key1 = Key.getKeyByIndex<Any>(keys[firstIndex])
      val key2 = Key.getKeyByIndex<Any>(keys[secondIndex])
      if (key1 == null && key2 == null) return VersionedUserDataFMap.empty()
      if (key1 == null) return VersionedUserDataFMap1(key2!!, payloadMapAt(secondIndex))
      if (key2 == null) return VersionedUserDataFMap1(key1, payloadMapAt(firstIndex))
      return VersionedUserDataFMap2(key1, payloadMapAt(firstIndex), key2, payloadMapAt(secondIndex))
    }
    return ArrayVersionedUserDataFMap(ArrayUtil.remove(keys, index),
                                      ArrayUtil.remove(values, index, ArrayUtil.OBJECT_ARRAY_FACTORY))
  }

  override fun cleanup(minVersion: Long): VersionedUserDataFMap = cleanupStorage(minVersion)

  override fun <V> plus(key: Key<V>, value: V & Any): KeyFMap = plusImpl(key, value)
  override fun minus(key: Key<*>): KeyFMap = minusImpl(key)
  override fun <V> get(key: Key<V>): V? = getImpl(key)
  override fun size(): Int = visibleSize()
  override fun isEmpty(): Boolean = isVisiblyEmpty()
  override fun getKeys(): Array<Key<*>> = visibleKeys()
  override fun getValueIdentityHashCode(): Int = visibleValueIdentityHashCode()
  override fun equalsByReference(other: KeyFMap): Boolean = visibleEqualsByReference(other)

  override fun toString(): String = currentSliceToString()
  override fun equals(other: Any?): Boolean = storageEquals(other)
  override fun hashCode(): Int = storageHashCode()
}

/**
 * Implementation of [VersionedUserDataFMap] for a large number of keys.
 *
 * It inherits the hash table of [MapBackedFMap] and adds the sorted array of key indices on top of it, because
 * [VersionedUserDataFMap] needs a stable positional view over its storage, and because a hash table lookup is
 * cheaper than the linear scan of [ArrayVersionedUserDataFMap]. Neither the table nor the array is ever mutated
 * in place, so every operation produces a new instance.
 */
private class MapVersionedUserDataFMap(
  private val sortedKeys: IntArray,
  payloadMaps: Array<Any>,
) : MapBackedFMap(sortedKeys, payloadMaps), VersionedUserDataFMap {

  override fun storageSize(): Int = sortedKeys.size
  override fun keyIndexAt(index: Int): Int = sortedKeys[index]
  override fun keyAt(index: Int): Key<*>? = Key.getKeyByIndex<Any>(sortedKeys[index])
  override fun payloadMapAt(index: Int): VersionedPayloadMap = valueAt(sortedKeys[index]) as VersionedPayloadMap
  override fun isStorageEmpty(): Boolean = false
  override fun findPayloadMap(key: Key<*>): VersionedPayloadMap? = valueAt(key.hashCode()) as VersionedPayloadMap?

  /**
   * The payload maps of the whole storage, in the [sortedKeys] order.
   */
  private fun payloadMaps(): Array<Any> = Array(sortedKeys.size) { valueAt(sortedKeys[it])!! }

  override fun withPayloadMap(key: Key<*>, payloadMap: VersionedPayloadMap?): VersionedUserDataFMap {
    val visiblePayloadMap = payloadMap.normalize()
    val keyCode = key.hashCode()
    val index = Arrays.binarySearch(sortedKeys, keyCode)
    if (index >= 0) {
      if (visiblePayloadMap == null) {
        return withoutEntryAt(index)
      }
      if (valueAt(keyCode) === visiblePayloadMap) {
        return this
      }
      val newPayloadMaps = payloadMaps()
      newPayloadMaps[index] = visiblePayloadMap
      // `sortedKeys` is never mutated in place, so it can be reused
      return MapVersionedUserDataFMap(sortedKeys, newPayloadMaps)
    }
    if (visiblePayloadMap == null) {
      return this
    }
    val insertionPoint = -index - 1
    return MapVersionedUserDataFMap(ArrayUtil.insert(sortedKeys, insertionPoint, keyCode),
                                    ArrayUtil.insert(payloadMaps(), insertionPoint, visiblePayloadMap as Any))
  }

  private fun withoutEntryAt(index: Int): VersionedUserDataFMap {
    val newKeys = ArrayUtil.remove(sortedKeys, index)
    val newPayloadMaps = ArrayUtil.remove(payloadMaps(), index, ArrayUtil.OBJECT_ARRAY_FACTORY)
    return if (newKeys.size > ArrayBackedFMap.ARRAY_THRESHOLD) {
      MapVersionedUserDataFMap(newKeys, newPayloadMaps)
    }
    else {
      ArrayVersionedUserDataFMap(newKeys, newPayloadMaps)
    }
  }

  override fun cleanup(minVersion: Long): VersionedUserDataFMap = cleanupStorage(minVersion)

  override fun <V> plus(key: Key<V>, value: V & Any): KeyFMap = plusImpl(key, value)
  override fun minus(key: Key<*>): KeyFMap = minusImpl(key)
  override fun <V> get(key: Key<V>): V? = getImpl(key)
  override fun size(): Int = visibleSize()
  override fun isEmpty(): Boolean = isVisiblyEmpty()
  override fun getKeys(): Array<Key<*>> = visibleKeys()
  override fun getValueIdentityHashCode(): Int = visibleValueIdentityHashCode()
  override fun equalsByReference(other: KeyFMap): Boolean = visibleEqualsByReference(other)

  override fun toString(): String = currentSliceToString()
  override fun equals(other: Any?): Boolean = storageEquals(other)
  override fun hashCode(): Int = storageHashCode()
}

private val EMPTY_KEYS_ARRAY: Array<Key<*>> = emptyArray()

// The `KeyFMap` contract, shared by every flavor. It cannot live in the interface as a set of default methods,
// because each flavor also inherits an implementation of the very same methods from its plain counterpart.

private fun <V> VersionedUserDataFMap.plusImpl(key: Key<V>, value: V & Any): KeyFMap {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  val oldPayloadMap = findPayloadMap(key)
  if (oldPayloadMap?.lowerBound(version) === value) {
    return runGarbageCollection()
  }
  val newPayloadMap = (oldPayloadMap ?: VersionedPayloadMap.empty()).insert(version, value)
                      ?: return runGarbageCollection()
  return withPayloadMap(key, newPayloadMap).runGarbageCollection()
}

private fun VersionedUserDataFMap.minusImpl(key: Key<*>): KeyFMap {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  val oldPayloadMap = findPayloadMap(key)
  if (oldPayloadMap?.lowerBound(version) == null) {
    return runGarbageCollection()
  }
  val newPayloadMap = oldPayloadMap.insert(version, null) ?: return runGarbageCollection()
  return withPayloadMap(key, newPayloadMap).runGarbageCollection()
}

@Suppress("UNCHECKED_CAST")
private fun <V> VersionedUserDataFMap.getImpl(key: Key<V>): V? {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  return findPayloadMap(key)?.lowerBound(version) as V?
}

private fun VersionedUserDataFMap.visibleSize(): Int {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  var result = 0
  for (i in 0 until storageSize()) {
    if (payloadMapAt(i).lowerBound(version) != null) {
      ++result
    }
  }
  return result
}

private fun VersionedUserDataFMap.isVisiblyEmpty(): Boolean {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  for (i in 0 until storageSize()) {
    if (payloadMapAt(i).lowerBound(version) != null) {
      return false
    }
  }
  return true
}

@Suppress("UNCHECKED_CAST")
private fun VersionedUserDataFMap.visibleKeys(): Array<Key<*>> {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  val storageSize = storageSize()
  if (storageSize == 0) {
    return EMPTY_KEYS_ARRAY
  }
  val result = arrayOfNulls<Key<*>>(storageSize)
  var visibleSize = 0
  for (i in 0 until storageSize) {
    if (payloadMapAt(i).lowerBound(version) == null) continue
    // a garbage collected key is unreachable for everybody, so it is dropped just like `ArrayBackedFMap` does
    val key = keyAt(i) ?: continue
    result[visibleSize++] = key
  }
  return when (visibleSize) {
    0 -> EMPTY_KEYS_ARRAY
    storageSize -> result as Array<Key<*>>
    else -> result.copyOf(visibleSize) as Array<Key<*>>
  }
}

/**
 * The formula repeats the one used by the regular [KeyFMap] implementations for the same slice of data,
 * which is possible because the storage is ordered by [Key.hashCode].
 */
private fun VersionedUserDataFMap.visibleValueIdentityHashCode(): Int {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  var hash = 0
  for (i in 0 until storageSize()) {
    val value = payloadMapAt(i).lowerBound(version) ?: continue
    hash = hash * 31 + keyIndexAt(i)
    hash = hash * 31 + System.identityHashCode(value)
  }
  return hash
}

@Suppress("UNCHECKED_CAST")
private fun VersionedUserDataFMap.visibleEqualsByReference(other: KeyFMap): Boolean {
  if (this === other) {
    return true
  }
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  var visibleSize = 0
  for (i in 0 until storageSize()) {
    val value = payloadMapAt(i).lowerBound(version) ?: continue
    ++visibleSize
    // a garbage collected key cannot be looked up in `other`, so the maps are conservatively reported as different
    val key = keyAt(i) ?: return false
    if (other.get(key as Key<Any>) !== value) {
      return false
    }
  }
  return visibleSize == other.size()
}

/**
 * Returns `null` if this payload map does not have anything visible for any version, and hence its key can be dropped.
 */
private fun VersionedPayloadMap?.normalize(): VersionedPayloadMap? {
  if (this == null || size() == 0) {
    return null
  }
  // a lone removal marker is invisible for every version, so there is no reason to keep the key around
  if (size() == 1 && lowerBound(Long.MAX_VALUE) == null) {
    return null
  }
  return this
}

/**
 * Returns the payload map without stale versions, `null` if the key can be dropped, or `this` if nothing has changed.
 */
private fun VersionedPayloadMap.cleaned(minVersion: Long): VersionedPayloadMap? {
  val cleanedPayloadMap = cleanupStaleVersions(minVersion) ?: this
  return cleanedPayloadMap.normalize()
}

/**
 * The [VersionedUserDataFMap.cleanup] of the index-addressed flavors, which also picks the flavor that fits
 * the number of survived keys.
 */
private fun VersionedUserDataFMap.cleanupStorage(minVersion: Long): VersionedUserDataFMap {
  // cleanup runs on every modification attempt, and normally it has nothing to do,
  // so the first pass only looks for changes and does not allocate anything.
  // The price is that the second pass has to compute the cleaned payload maps again, which is affordable for a rare occasions.
  val storageSize = storageSize()
  var survivedCount = 0
  var changed = false
  for (i in 0 until storageSize) {
    val payloadMap = payloadMapAt(i)
    val cleanedPayloadMap = payloadMap.cleaned(minVersion)
    if (cleanedPayloadMap !== payloadMap) {
      changed = true
    }
    if (cleanedPayloadMap != null) {
      ++survivedCount
    }
  }
  if (!changed) {
    return this
  }
  if (survivedCount > 2) {
    val newKeys = IntArray(survivedCount)
    val newPayloadMaps = arrayOfNulls<Any>(survivedCount)
    var index = 0
    for (i in 0 until storageSize) {
      val cleanedPayloadMap = payloadMapAt(i).cleaned(minVersion) ?: continue
      newKeys[index] = keyIndexAt(i)
      newPayloadMaps[index] = cleanedPayloadMap
      ++index
    }
    @Suppress("UNCHECKED_CAST")
    val survivedPayloadMaps = newPayloadMaps as Array<Any>
    return if (survivedCount > ArrayBackedFMap.ARRAY_THRESHOLD) {
      MapVersionedUserDataFMap(newKeys, survivedPayloadMaps)
    }
    else {
      ArrayVersionedUserDataFMap(newKeys, survivedPayloadMaps)
    }
  }
  var result: VersionedUserDataFMap = VersionedUserDataFMap.empty()
  for (i in 0 until storageSize) {
    val cleanedPayloadMap = payloadMapAt(i).cleaned(minVersion) ?: continue
    val key = keyAt(i) ?: continue
    result = result.withPayloadMap(key, cleanedPayloadMap)
  }
  return result
}

private fun VersionedUserDataFMap.runGarbageCollection(): VersionedUserDataFMap {
  val registry = InternalPsiVersioning.PsiVersionRegistry.instance
  var minVersion = Long.MAX_VALUE
  for (version in registry.getFrozenKeys()) {
    minVersion = minOf(minVersion, version)
  }
  return cleanup(minVersion)
}

private fun VersionedUserDataFMap.currentSliceToString(): String {
  val version = InternalPsiVersioning.getCurrentPsiVersion()
  val builder = StringBuilder("{")
  for (i in 0 until storageSize()) {
    val value = payloadMapAt(i).lowerBound(version) ?: continue
    if (builder.length > 1) {
      builder.append(", ")
    }
    builder.append(keyAt(i)).append("=").append(value)
  }
  return builder.append("}").toString()
}

/**
 * Compares the whole versioned storage, and not only the slice that is visible for the current version.
 */
private fun VersionedUserDataFMap.storageEquals(other: Any?): Boolean {
  if (this === other) {
    return true
  }
  if (other !is VersionedUserDataFMap) {
    return false
  }
  val storageSize = storageSize()
  if (storageSize != other.storageSize()) {
    return false
  }
  // both storages are ordered by `Key.hashCode()`, so a positional comparison is enough
  for (i in 0 until storageSize) {
    if (keyIndexAt(i) != other.keyIndexAt(i) || payloadMapAt(i) != other.payloadMapAt(i)) {
      return false
    }
  }
  return true
}

private fun VersionedUserDataFMap.storageHashCode(): Int {
  var hash = 0
  for (i in 0 until storageSize()) {
    hash += keyIndexAt(i) xor payloadMapAt(i).hashCode()
  }
  return hash
}
