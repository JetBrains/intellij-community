// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Debug

/**
 * A copy-on-write map from [Long] to [Any], tailored to the use-case of persistent syntax trees.
 * Has small memory footprint. `null` values are permitted.
 */
// implementation details:
// Although this structure is a map, we can treat it as an array of tuples, since it saves us from overhead on map internals.
// But we go even further, and instead represent this structure as a tuple of arrays.
// This saves us from allocation of objects that represent pairs, and it also improves cache locality.
// The tuples are sorted by versions, as it allows us to perform efficient lookups.
// Every operation produces a new instance of this structure in order to make it friendlier to lock-free algorithms.
// `versions` and `payloads` are always of the same size
@ApiStatus.Internal
@Debug.Renderer(hasChildren = "size() != 0", childrenArray = "arrayOfPairs()")
class VersionedPayloadMap private constructor(
  private val versions: LongArray,
  private val payloads: Array<Any?>,
) {
  companion object {
    @JvmStatic
    fun create(firstVersion: Long, firstPayload: Any?, secondVersion: Long, secondPayload: Any?): VersionedPayloadMap {
      return VersionedPayloadMap(longArrayOf(firstVersion, secondVersion), arrayOf(firstPayload, secondPayload))
    }
  }

  fun size(): Int {
    return versions.size
  }

  /**
   * Inserts [payload] with [version] into the map.
   * The invariant of sortedness is retained after insertion.
   *
   * @return `null` if insertion is not needed, or a new instance of with the updated data otherwise
   */
  fun insert(version: Long, payload: Any?): VersionedPayloadMap? {
    var index = payloads.size - 1
    val newVersions: LongArray
    val newPayloads: Array<Any?>
    while (index >= 0) {
      if (versions[index] == version) {
        if (payloads[index] === payload) {
          return null
        }
        newVersions = versions
        newPayloads = arrayOfNulls(payloads.size)
        System.arraycopy(payloads, 0, newPayloads, 0, payloads.size)
        newPayloads[index] = payload
        return VersionedPayloadMap(newVersions, newPayloads)
      }
      if (versions[index] < version) {
        newVersions = versions.copyOf(versions.size + 1)
        newVersions[index + 1] = version
        System.arraycopy(versions, index + 1, newVersions, index + 2, versions.size - index - 1)
        newPayloads = payloads.copyOf(payloads.size + 1)
        newPayloads[index + 1] = payload
        System.arraycopy(payloads, index + 1, newPayloads, index + 2, payloads.size - index - 1)
        return VersionedPayloadMap(newVersions, newPayloads)
      }
      index--
    }
    newVersions = LongArray(versions.size + 1)
    newVersions[0] = version
    System.arraycopy(versions, 0, newVersions, 1, versions.size)
    newPayloads = arrayOfNulls(payloads.size + 1)
    newPayloads[0] = payload
    System.arraycopy(payloads, 0, newPayloads, 1, payloads.size)
    return VersionedPayloadMap(newVersions, newPayloads)
  }

  /**
   * Removes all entries where version is smaller than [threshold].
   *
   * @return `null` if no cleanup is needed, or a new instance of with the updated data otherwise
   */
  fun cleanupStaleVersions(threshold: Long): VersionedPayloadMap? {
    var i = 0
    // since versions are ordered, we can perform garbage collection efficiently
    while (i < payloads.size) {
      if (versions[i] > threshold) {
        break
      }
      i++
    }
    if (i <= 1) {
      // there are no elements to cleanup
      return null
    }
    val newElements: Array<Any?> = payloads.copyOfRange(i - 1, payloads.size)
    val newVersions: LongArray = versions.copyOfRange(i - 1, versions.size)
    return VersionedPayloadMap(newVersions, newElements)
  }

  /**
   * Returns the payload with the biggest version that is not greater than [targetVersion],
   * or `null` if there is no such element or the element was explicitly removed.
   */
  fun lowerBound(targetVersion: Long): Any? {
    var i = versions.size - 1
    while (i >= 0) {
      if (targetVersion >= versions[i]) {
        return payloads[i]
      }
      --i
    }
    return null
  }

  /**
   * Returns `true` if the element with [targetVersion] was explicitly removed (by setting `null`), `false` otherwise.
   */
  fun explicitlyRemoved(targetVersion: Long): Boolean {
    var i = versions.size - 1
    while (i >= 0) {
      if (targetVersion == versions[i]) {
        return payloads[i] == null
      }
      if (targetVersion > versions[i]) {
        break
      }
      --i
    }
    return false
  }

  @Suppress("unused")
  private fun arrayOfPairs(): Array<VersionedPayload> =
    versions.zip(payloads).map { VersionedPayload(it.first, it.second) }.toTypedArray()

  private data class VersionedPayload(val version: Long, val payload: Any?)

  override fun toString(): String =
    arrayOfPairs().joinToString(prefix = "[", postfix = "]", separator = ", ") { (v, p) -> "$v => $p" }
}
