// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import org.jetbrains.annotations.ApiStatus
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function

/**
 * A concurrent map that stores [strong][java.lang.ref] keys and [weak][WeakReference] [versioned][VersionedPsiReference] values.
 *
 * The intended use-case is lifecycle management of objects that are outside of one's control
 * (like Platform's handling of major IDE entities, e.g. [com.intellij.openapi.editor.Document] or [com.intellij.psi.FileViewProvider])
 * where at the same time these objects should be fixed for older PSI version snapshots.
 *
 * ## Lifecycle of values
 *
 * Imagine that the map contains a value `A` for key `X` at version 5 and a value `B` for the same key at version 6.
 * We can graphically represent such map as `['X' => [5 => 'A', 6 => 'B']]`.
 *
 * ### Weakly reachable latest value
 *
 * Assume the original map `['X' => [5 => 'A']]`. Now assume that the value `A` becomes weakly reachable.
 * At the next access to the map, it shall perform cleanup of weakly referenced values, and as a result the map will be empty (i.e., `[]`).
 *
 * ### Overwrite with new value
 *
 * Assume the original map `['X' => [5 => 'A']]`. Now assume that we set a new value `B` for `X` at version `6`.
 * The new representation will be `['X' => [5 => 'A', 6 => 'B']]`. As long as `A` is not weakly reachable AND version `5` might be needed by someone,
 * `A` will remain in the map.
 *
 * If version `5` is no longer needed, the map will remove all entries for version `5`.
 * I.e., if the globally published version advances to `6`, the map will be updated to `['X' => [6 => 'B']]`.
 */
@ApiStatus.Internal
class ConcurrentWeakVersionedValueHashMap<K: Any, V: Any> : ConcurrentMap<K, V>, PsiVersionCleanable {

  private val referenceQueue = ReferenceQueue<V>()

  // K -> Long -> WeakReference<V>.
  // We could have a map K -> VersionedPsiReference<V>,
  // but it is more optimal to track and cleanup the entire map instead of each versioned reference.
  private val actualMap: ConcurrentMap<K, VersionedPayloadMap> = ConcurrentHashMap()

  init {
    InternalPsiVersioning.PsiVersionRegistry.instance.registerCleanable(this)
  }

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = entrySnapshotForCurrentVersion().mapTo(LinkedHashSet()) { (key, value) -> Entry(key, value) }

  override fun isEmpty(): Boolean = size == 0

  override fun containsKey(key: K): Boolean = get(key) != null

  override fun containsValue(value: V?): Boolean {
    return value != null && entrySnapshotForCurrentVersion().any { it.second == value }
  }

  override fun get(key: K): V? {
    processQueue()
    return actualMap[key]?.getLiveValue()
  }

  override fun put(key: K, value: V): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var previousValue: V? = null
    actualMap.compute(key) { _, payloadMap ->
      val actualPayloadMap = payloadMap ?: VersionedPayloadMap.empty()
      previousValue = actualPayloadMap.getLiveVersionedValue(version)
      actualPayloadMap.withPayload(version, createReference(key, value))
    }
    return previousValue
  }

  override fun remove(key: K): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var removedValue: V? = null
    actualMap.computeIfPresent(key) { _, payloadMap ->
      removedValue = payloadMap.getLiveVersionedValue(version)
      if (removedValue == null) {
        return@computeIfPresent keepPayloadMapIfNeeded(payloadMap)
      }
      keepPayloadMapIfNeeded(payloadMap.withPayload(version, null))
    }
    return removedValue
  }

  override val size: Int
    get() = entrySnapshotForCurrentVersion().size

  override val keys: MutableSet<K>
    get() = entrySnapshotForCurrentVersion().mapTo(LinkedHashSet()) { it.first }

  override val values: MutableCollection<V>
    get() = entrySnapshotForCurrentVersion().mapTo(ArrayList()) { it.second }

  override fun forEach(action: BiConsumer<in K, in V>) {
    for ((key, value) in entrySnapshotForCurrentVersion()) {
      action.accept(key, value)
    }
  }

  override fun putIfAbsent(key: K, value: V): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var previousValue: V? = null
    actualMap.compute(key) { _, payloadMap ->
      val currentValue = payloadMap?.getLiveVersionedValue(version)
      if (currentValue != null) {
        previousValue = currentValue
        return@compute payloadMap
      }

      val actualPayloadMap = payloadMap ?: VersionedPayloadMap.empty()
      actualPayloadMap.withPayload(version, createReference(key, value))
    }
    return previousValue
  }

  override fun remove(key: K, value: V): Boolean {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var removed = false
    actualMap.computeIfPresent(key) { _, payloadMap ->
      val currentReference = payloadMap.getVersionedReference(version)
      if (currentReference == createReferenceForComparison(key, value)) {
        removed = true
        return@computeIfPresent keepPayloadMapIfNeeded(payloadMap.withPayload(version, null))
      }
      keepPayloadMapIfNeeded(payloadMap)
    }
    return removed
  }

  // todo: atomicity is broken
  override fun putAll(from: Map<out K, V>) {
    for ((key, value) in from) {
      put(key, value)
    }
  }

  override fun clear() {
    processQueue()
    // this implementation retains behavior where `clear` does not perform compaction of the internal map.
    for (key in actualMap.keys) {
      remove(key)
    }
  }

  override fun replace(key: K, oldValue: V, newValue: V): Boolean {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var replaced = false
    actualMap.computeIfPresent(key) { _, payloadMap ->
      val currentReference = payloadMap.getVersionedReference(version)
      if (currentReference == createReferenceForComparison(key, oldValue)) {
        replaced = true
        return@computeIfPresent keepPayloadMapIfNeeded(payloadMap.withPayload(version, createReference(key, newValue)))
      }
      keepPayloadMapIfNeeded(payloadMap)
    }
    return replaced
  }

  override fun replace(key: K, value: V): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var previousValue: V? = null
    actualMap.computeIfPresent(key) { _, payloadMap ->
      val currentValue = payloadMap.getLiveVersionedValue(version)
      if (currentValue == null) {
        return@computeIfPresent keepPayloadMapIfNeeded(payloadMap)
      }
      previousValue = currentValue
      keepPayloadMapIfNeeded(payloadMap.withPayload(version, createReference(key, value)))
    }
    return previousValue
  }

  // todo: atomicity is broken
  override fun replaceAll(function: BiFunction<in K, in V, out V?>) {
    for ((key, initialValue) in entrySnapshotForCurrentVersion()) {
      var oldValue = initialValue
      while (true) {
        val newValue = function.apply(key, oldValue)
        val replaced = if (newValue == null) remove(key, oldValue) else replace(key, oldValue, newValue)
        if (replaced) {
          break
        }
        oldValue = get(key) ?: break
      }
    }
  }

  override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V?>): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var result: V? = null
    actualMap.compute(key) { _, payloadMap ->
      val currentValue = payloadMap?.getLiveVersionedValue(version)
      if (currentValue != null) {
        result = currentValue
        return@compute payloadMap
      }

      val newValue = mappingFunction.apply(key)
      if (newValue == null) {
        return@compute payloadMap?.let { keepPayloadMapIfNeeded(it) }
      }

      val actualPayloadMap = payloadMap ?: VersionedPayloadMap.empty()
      result = newValue
      actualPayloadMap.withPayload(version, createReference(key, newValue))
    }
    return result
  }

  override fun computeIfPresent(key: K, remappingFunction: BiFunction<in K, in V, out V?>): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var result: V? = null
    actualMap.computeIfPresent(key) { _, payloadMap ->
      val currentValue = payloadMap.getLiveVersionedValue(version)
      if (currentValue == null) {
        return@computeIfPresent keepPayloadMapIfNeeded(payloadMap)
      }

      val newValue = remappingFunction.apply(key, currentValue)
      val newPayloadMap = if (newValue == null) {
        payloadMap.withPayload(version, null)
      }
      else {
        result = newValue
        payloadMap.withPayload(version, createReference(key, newValue))
      }
      keepPayloadMapIfNeeded(newPayloadMap)
    }
    return result
  }

  override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var result: V? = null
    actualMap.compute(key) { _, payloadMap ->
      val currentValue = payloadMap?.getLiveVersionedValue(version)
      val newValue = remappingFunction.apply(key, currentValue)
      if (newValue == null) {
        if (currentValue == null) {
          return@compute payloadMap?.let { keepPayloadMapIfNeeded(it) }
        }
        return@compute keepPayloadMapIfNeeded(payloadMap.withPayload(version, null))
      }

      val actualPayloadMap = payloadMap ?: VersionedPayloadMap.empty()
      result = newValue
      actualPayloadMap.withPayload(version, createReference(key, newValue))
    }
    return result
  }

  override fun merge(key: K, value: V, remappingFunction: BiFunction<in V, in V, out V?>): V? {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    var result: V? = null
    actualMap.compute(key) { _, payloadMap ->
      val currentValue = payloadMap?.getLiveVersionedValue(version)
      if (currentValue == null) {
        val actualPayloadMap = payloadMap ?: VersionedPayloadMap.empty()
        result = value
        return@compute actualPayloadMap.withPayload(version, createReference(key, value))
      }

      val newValue = remappingFunction.apply(currentValue, value)
      val newPayloadMap = if (newValue == null) {
        payloadMap.withPayload(version, null)
      }
      else {
        result = newValue
        payloadMap.withPayload(version, createReference(key, newValue))
      }
      keepPayloadMapIfNeeded(newPayloadMap)
    }
    return result
  }

  /**
   * Slice of values for the currently used PSI version
   */
  private fun entrySnapshotForCurrentVersion(): List<Pair<K, V>> {
    processQueue()
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    val result = ArrayList<Pair<K, V>>()
    for ((key, payloadMap) in actualMap) {
      val value = payloadMap.getLiveVersionedValue(version)
      if (value == null) {
        cleanupEntry(key, payloadMap)
      }
      else {
        result.add(key to value)
      }
    }
    return result
  }

  /**
   * Cleanup all stale values. The value gets removed if it is weakly reachable AND its version is obsolete.
   */
  private fun processQueue() {
    while (true) {
      val reference = referenceQueue.poll() ?: return
      @Suppress("UNCHECKED_CAST")
      actualMap.computeIfPresent((reference as WeakValueReference<K, V>).key) { _, payloadMap ->
        keepPayloadMapIfNeeded(payloadMap)
      }
    }
  }

  private fun cleanupEntry(key: K, payloadMap: VersionedPayloadMap) {
    actualMap.computeIfPresent(key) { _, currentPayloadMap ->
      if (currentPayloadMap !== payloadMap) {
        currentPayloadMap
      }
      else {
        keepPayloadMapIfNeeded(currentPayloadMap)
      }
    }
  }

  private fun cleanupStaleEntry(key: K, payloadMap: VersionedPayloadMap, minVersion: Long) {
    actualMap.computeIfPresent(key) { _, currentPayloadMap ->
      if (currentPayloadMap !== payloadMap) {
        currentPayloadMap
      }
      else {
        currentPayloadMap.cleanupStaleVersions(minVersion) ?: currentPayloadMap
      }
    }
  }

  /**
   * Checks whether it makes sense to retain the existing payload map
   */
  private fun keepPayloadMapIfNeeded(payloadMap: VersionedPayloadMap): VersionedPayloadMap? {
    val activeVersions = InternalPsiVersioning.PsiVersionRegistry.instance.getFrozenKeys().toList()
    return if (payloadMap.getLiveValue() != null || payloadMap.isLiveVisibleInAnyVersion(activeVersions)) {
      payloadMap
    } else {
      null
    }
  }

  /**
   * Inserts [payload] to [this]
   */
  private fun VersionedPayloadMap.withPayload(version: Long, payload: WeakValueReference<K, V>?): VersionedPayloadMap {
    return insert(version, payload) ?: this
  }

  private fun VersionedPayloadMap.getLiveValue(): V? {
    return getLiveVersionedValue(InternalPsiVersioning.getCurrentPsiVersion())
  }

  private fun VersionedPayloadMap.getLiveVersionedValue(version: Long): V? {
    return getVersionedReference(version)?.get()
  }

  private fun VersionedPayloadMap.getVersionedReference(version: Long): WeakValueReference<K, V>? {
    @Suppress("UNCHECKED_CAST")
    return lowerBound(version) as WeakValueReference<K, V>?
  }

  private fun VersionedPayloadMap.isLiveVisibleInAnyVersion(versions: Collection<Long>): Boolean {
    return versions.any { getLiveVersionedValue(it) != null }
  }

  override fun liveVersionChanged(minVersion: Long, liveVersions: Set<Long>) {
    for ((key, payloadMap) in actualMap) {
      cleanupStaleEntry(key, payloadMap, minVersion)
    }
  }

  private fun createReference(key: K, value: V): WeakValueReference<K, V> {
    return WeakValueReference(key, value, referenceQueue)
  }

  private fun createReferenceForComparison(key: K, value: V): WeakValueReference<K, V> {
    return WeakValueReference(key, value, null)
  }

  private inner class Entry(
    override val key: K,
    value: V,
  ) : MutableMap.MutableEntry<K, V> {
    override var value: V = value
      private set

    override fun setValue(newValue: V): V {
      val oldValue = put(key, newValue) ?: value
      value = newValue
      return oldValue
    }

    override fun equals(other: Any?): Boolean {
      return other is Map.Entry<*, *> && key == other.key && value == other.value
    }

    override fun hashCode(): Int {
      return key.hashCode() xor value.hashCode()
    }

    override fun toString(): String {
      return "$key=$value"
    }
  }

  private class WeakValueReference<K: Any, V: Any>(
    val key: K,
    value: V,
    queue: ReferenceQueue<V>?,
  ) : WeakReference<V>(value, queue) {
    override fun equals(other: Any?): Boolean {
      if (this === other) {
        return true
      }
      if (other !is WeakValueReference<*, *>) {
        return false
      }

      val value = get()
      return key == other.key && value != null && value == other.get()
    }

    override fun hashCode(): Int {
      return 31 * key.hashCode() + (get()?.hashCode() ?: 0)
    }
  }
}
