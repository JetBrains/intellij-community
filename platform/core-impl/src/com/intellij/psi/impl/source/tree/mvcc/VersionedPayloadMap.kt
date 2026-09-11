// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Debug

/**
 * A copy-on-write sorted map from [Long] to [Any], tailored to the use-case of persistent syntax trees.
 * Has small memory footprint. `null` values are permitted.
 *
 * Some modification operations can return `null` if no modifications were performed --
 * it helps to avoid unnecessary `compareAndSet` operations for the client of this data structure.
 *
 * The map is untyped because we did not want to annoy ourselves with the array factories; this data structure is low-level anyway.
 *
 * ### Reachability
 *
 * One important part of this structure is the relation of _reachability_.
 *
 * Informally, reachability describes how a snapshot at one version can use snapshots in previous versions.
 * If version `A` is reachable from version `B`, then snapshot `A` can refer to the data stored for `B`.
 * This is important to ensure efficient reuse of data computed by previous versions in newer versions.
 *
 * Formally, reachability is a partial order on [Long] that is generated as a reflexive and transitive closure from the following rule:
 *
 * - **If `x` is even, then both `x + 1` and `x + 2` are reachable from `x`**
 *
 * If we denote reachability as `->`, then an example of reachability can be the following:
 * ```text
 * 0 -> 2, 2 -> 4, 4 -> 6, ...
 * 0 -> 1, 2 -> 3, 4 -> 5, ...
 * ```
 * Note, that we don't have `1 -> 2`: `2` is not reachable from `1`. In other words, we permit only one fork per each main version at this point.
 *
 * Another way to visualize reachability is by looking at the following picture:
 * ```text
 *         -- 3     -- 5     -- 7
 *        /        /        /
 *  >--- 2 ------ 4 ------ 6 ----- -->
 * ```
 * The formal definition of reachability is an implementation detail, and it can evolve alongside versioned syntax structure.
 *
 * The implementation of this structure is tightly coupled with
 * [InternalPsiVersioning.MAIN_TIMELINE_DELTA], [InternalPsiVersioning.FORKED_TIMELINE_DELTA]
 *
 * Structural changes in these classes should be reflected in `org.jetbrains.idea.devkit.hprof.PersistentSyntaxTreeHprofProcessor`
 */
@ApiStatus.Internal
@Debug.Renderer(hasChildren = "size() != 0", childrenArray = "arrayOfPairs()")
sealed interface VersionedPayloadMap {
  companion object {

    @JvmStatic
    fun create(firstVersion: Long, firstPayload: Any?, secondVersion: Long, secondPayload: Any?): VersionedPayloadMap {
      return if (firstVersion <= secondVersion) {
        VersionedPayloadMap2(firstVersion, firstPayload, secondVersion, secondPayload)
      }
      else {
        VersionedPayloadMap2(secondVersion, secondPayload, firstVersion, firstPayload)
      }
    }


    @JvmStatic
    fun empty(): VersionedPayloadMap = VersionedPayloadMap0
  }

  fun size(): Int

  /**
   * Inserts [payload] with [version] into the map.
   * The invariant of sortedness is retained after insertion.
   *
   * @return `null` if insertion is not needed (i.e. [payload] is already present for [version]),
   * or a new instance with the updated data otherwise
   */
  fun insert(version: Long, payload: Any?): VersionedPayloadMap?

  /**
   * Removes the entries that reach the same versions that [barrier] can reach.
   *
   * This method is the main actor of garbage collection: if at some point we realize that the version history has advanced far enough,
   * we can clean the references to "old" versions.
   *
   * The map needs to return the same results on [lowerBound] as if [cleanupStaleVersions] was not invoked.
   * ```kotlin
   * // for every `version` reachable from `barrier`:
   * map.cleanupStaleVersions(barrier).lowerBound(version) == map.lowerBound(version)
   * ```
   *
   * The passed [barrier] always belongs to the main timeline, i.e., it is even.
   *
   * @return `null` if no changes in the map were performed, or a new instance of with the updated data otherwise
   */
  fun cleanupStaleVersions(barrier: Long): VersionedPayloadMap?

  /**
   * Returns the payload with the closest version from which [targetVersion] is [isReachable],
   * or `null` if there is no such element or the element was explicitly removed.
   *
   * ```kotlin
   * emptyMap.insert(10, "a").lowerBound(12) == "a"
   * emptyMap.insert(10, "a").lowerBound(8) == null
   * ```
   *
   */
  fun lowerBound(targetVersion: Long): Any?

  /**
   * Returns `true` if the element with [targetVersion] was explicitly removed (by setting `null`), `false` otherwise.
   */
  fun explicitlyRemoved(targetVersion: Long): Boolean

  @Suppress("unused") // user in `@Debug.Renderer`
  fun arrayOfPairs(): Array<VersionedPayload>
}

/**
 * Space-optimized flavor of empty [VersionedPayloadMap].
 */
@Debug.Renderer(hasChildren = "false", childrenArray = "arrayOf<VersionedPayloadMap>()")
private object VersionedPayloadMap0: VersionedPayloadMap {
  override fun size(): Int = 0
  override fun insert(version: Long, payload: Any?): VersionedPayloadMap = VersionedPayloadMap1(version, payload)
  override fun cleanupStaleVersions(barrier: Long): VersionedPayloadMap? = null
  override fun lowerBound(targetVersion: Long): Any? = null
  override fun explicitlyRemoved(targetVersion: Long): Boolean = false
  override fun arrayOfPairs(): Array<VersionedPayload> = emptyArray()
}

/**
 * Space-optimized flavor of [VersionedPayloadMap] that stores one version and payload.
 * Avoids memory overhead on arrays.
 */
private class VersionedPayloadMap1(
  private val version: Long,
  private val payload: Any?
): VersionedPayloadMap {

  override fun size(): Int = 1

  override fun insert(version: Long, payload: Any?): VersionedPayloadMap? {
    if (version == this.version) {
      return if (this.payload === payload) null else VersionedPayloadMap1(version, payload)
    }
    return if (version > this.version) {
      VersionedPayloadMap2(this.version, this.payload, version, payload)
    }
    else {
      VersionedPayloadMap2(version, payload, this.version, this.payload)
    }
  }

  override fun cleanupStaleVersions(barrier: Long): VersionedPayloadMap? {
    return if (isCollectable(version, barrier)) {
      VersionedPayloadMap0
    }
    else {
      null
    }
  }

  override fun lowerBound(targetVersion: Long): Any? {
    return if (isReachable(version, targetVersion)) payload else null
  }

  override fun explicitlyRemoved(targetVersion: Long): Boolean {
    return targetVersion == version && payload == null
  }

  override fun arrayOfPairs(): Array<VersionedPayload> = arrayOf(VersionedPayload(version, payload))

  override fun toString(): String =
    "[$version => $payload]"
}

/**
 * Space-optimized flavor of [VersionedPayloadMap] that stores two versions and payloads.
 * Avoids memory overhead on arrays.
 */
private class VersionedPayloadMap2(
  private val version1: Long,
  private val payload1: Any?,
  private val version2: Long,
  private val payload2: Any?
): VersionedPayloadMap {

  override fun size(): Int = 2

  override fun insert(version: Long, payload: Any?): VersionedPayloadMap? {
    if (version == version2) {
      return if (payload2 === payload) null else VersionedPayloadMap2(version1, payload1, version, payload)
    }
    if (version == version1) {
      return if (payload1 === payload) null else VersionedPayloadMap2(version, payload, version2, payload2)
    }

    if (version > version2) {
      return ArrayVersionedPayloadMap(longArrayOf(version1, version2, version), arrayOf(payload1, payload2, payload))
    }
    if (version > version1) {
      return ArrayVersionedPayloadMap(longArrayOf(version1, version, version2), arrayOf(payload1, payload, payload2))
    }
    return ArrayVersionedPayloadMap(longArrayOf(version, version1, version2), arrayOf(payload, payload1, payload2))
  }

  override fun cleanupStaleVersions(barrier: Long): VersionedPayloadMap? {
    return if (isReachable(version2, barrier)) {
      VersionedPayloadMap1(version2, payload2)
    }
    else if (isCollectable(version1, barrier)) {
      if (isCollectable(version2, barrier)) VersionedPayloadMap0 else VersionedPayloadMap1(version2, payload2)
    }
    else if (isCollectable(version2, barrier) && isReachable(version1, barrier)) {
      // `version2` belongs to a forked timeline that no live version can observe
      VersionedPayloadMap1(version1, payload1)
    } else {
      // `version2` cannot stand in for `version1`: it is either not stale yet, or it belongs to a live forked timeline
      null
    }
  }

  override fun lowerBound(targetVersion: Long): Any? {
    if (isReachable(version2, targetVersion)) {
      return payload2
    }
    if (isReachable(version1, targetVersion)) {
      return payload1
    }
    return null
  }

  override fun explicitlyRemoved(targetVersion: Long): Boolean {
    if (targetVersion == version2) {
      return payload2 == null
    }
    if (targetVersion == version1) {
      return payload1 == null
    }
    return false
  }

  override fun arrayOfPairs(): Array<VersionedPayload> {
    return arrayOf(VersionedPayload(version1, payload1), VersionedPayload(version2, payload2))
  }

  override fun toString(): String =
    "[$version1 => $payload1, $version2 => $payload2]"
}

/**
 * Implementation of [VersionedPayloadMap] for arbitrary number of elements.
 */
// implementation details:
// Although this structure is a map, we can treat it as an array of tuples, since it saves us from overhead on map internals.
// But we go even further, and instead represent this structure as a tuple of arrays.
// This saves us from allocation of objects that represent pairs, and it also improves cache locality.
// The tuples are sorted by versions, as it allows us to perform efficient lookups.
// Every operation produces a new instance of this structure in order to make it friendlier to lock-free algorithms.
// `versions` and `payloads` are always of the same size
@Debug.Renderer(hasChildren = "size() != 0", childrenArray = "arrayOfPairs()")
private class ArrayVersionedPayloadMap(
  private val versions: LongArray,
  private val payloads: Array<Any?>,
) : VersionedPayloadMap {

  override fun size(): Int {
    return versions.size
  }

  override fun insert(version: Long, payload: Any?): VersionedPayloadMap? {
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
        return ArrayVersionedPayloadMap(newVersions, newPayloads)
      }
      if (versions[index] < version) {
        newVersions = versions.copyOf(versions.size + 1)
        newVersions[index + 1] = version
        System.arraycopy(versions, index + 1, newVersions, index + 2, versions.size - index - 1)
        newPayloads = payloads.copyOf(payloads.size + 1)
        newPayloads[index + 1] = payload
        System.arraycopy(payloads, index + 1, newPayloads, index + 2, payloads.size - index - 1)
        return ArrayVersionedPayloadMap(newVersions, newPayloads)
      }
      index--
    }
    newVersions = LongArray(versions.size + 1)
    newVersions[0] = version
    System.arraycopy(versions, 0, newVersions, 1, versions.size)
    newPayloads = arrayOfNulls(payloads.size + 1)
    newPayloads[0] = payload
    System.arraycopy(payloads, 0, newPayloads, 1, payloads.size)
    return ArrayVersionedPayloadMap(newVersions, newPayloads)
  }

  override fun cleanupStaleVersions(barrier: Long): VersionedPayloadMap? {
    // since versions are ordered, we can perform garbage collection efficiently -- we iterate from the end
    // everything below the newest even entry at or below the barrier is invisible to every live version
    var i = versions.size - 1
    while (i >= 0) {
      if (isReachable(versions[i], barrier)) {
        break
      }
      i--
    }
    if (i < 0) {
      i = 0
    }
    // the retained range can still hold forked entries that the barrier cannot reach, and they go away as well.
    // the first pass only counts the survivors, so we allocate nothing when there is nothing to do
    var survivorCount = 0
    for (j in i until versions.size) {
      if (!isCollectable(versions[j], barrier)) {
        ++survivorCount
      }
    }
    if (survivorCount == versions.size) {
      // there are no elements to cleanup
      return null
    }
    val newPayloads: Array<Any?>
    val newVersions: LongArray
    if (survivorCount == versions.size - i) {
      // every entry of the retained range survives, so we copy the range at once
      newPayloads = payloads.copyOfRange(i, payloads.size)
      newVersions = versions.copyOfRange(i, versions.size)
    }
    else {
      newPayloads = arrayOfNulls<Any?>(survivorCount)
      newVersions = LongArray(survivorCount)
      var k = 0
      for (j in i until versions.size) {
        if (isCollectable(versions[j], barrier)) {
          continue
        }
        newVersions[k] = versions[j]
        newPayloads[k] = payloads[j]
        ++k
      }
    }
    return createVersionedPayloadMap(newVersions, newPayloads)
  }

  override fun lowerBound(targetVersion: Long): Any? {
    var i = versions.size - 1
    while (i >= 0) {
      if (isReachable(versions[i], targetVersion)) {
        return payloads[i]
      }
      --i
    }
    return null
  }

  override fun explicitlyRemoved(targetVersion: Long): Boolean {
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

  override fun arrayOfPairs(): Array<VersionedPayload> =
    versions.zip(payloads).map { VersionedPayload(it.first, it.second) }.toTypedArray()

  override fun toString(): String =
    arrayOfPairs().joinToString(prefix = "[", postfix = "]", separator = ", ") { (v, p) -> "$v => $p" }
}


/**
 * Returns `true` if [laterVersion] is reachable from [earlierVersion].
 */
internal fun isReachable(earlierVersion: Long, laterVersion: Long): Boolean {
  return laterVersion == earlierVersion || (laterVersion > earlierVersion && !earlierVersion.isForked())
}

private fun Long.isForked(): Boolean = this % InternalPsiVersioning.MAIN_TIMELINE_DELTA == InternalPsiVersioning.FORKED_TIMELINE_DELTA

/**
 * Returns `true` if the payload stored for [version] is invisible to every version that is reachable from [barrier].
 *
 * A forked version is observable by itself only, so a forked entry below the [barrier] is garbage.
 */
private fun isCollectable(version: Long, barrier: Long): Boolean {
  return version.isForked() && barrier > version
}

private fun createVersionedPayloadMap(versions: LongArray, payloads: Array<Any?>): VersionedPayloadMap {
  return when (versions.size) {
    0 -> VersionedPayloadMap.empty()
    1 -> VersionedPayloadMap1(versions[0], payloads[0])
    2 -> VersionedPayloadMap2(versions[0], payloads[0], versions[1], payloads[1])
    else -> ArrayVersionedPayloadMap(versions, payloads)
  }
}

@ApiStatus.Internal
data class VersionedPayload(val version: Long, val payload: Any?)
