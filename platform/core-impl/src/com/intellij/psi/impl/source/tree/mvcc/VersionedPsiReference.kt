// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import com.intellij.util.containers.VarHandleWrapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Debug

/**
 * An atomic reference to a value that supports PSI versioning.
 *
 * This class is needed for imperative code that operates with references which have to be available in snapshots of older PSI versions.
 * The main property of this reference is that overwriting values does not make them eligible to garbage collection --
 * the values are kept [strongly reachable][java.lang.ref] if the Platform thinks that they might be needed to some versioned snapshot.
 *
 * Example:
 * ```kotlin
 * val version = VersionedPsiReference<String>("a")
 * freezePsiVersion { // capturing the existing version
 *   assert(version.get() == "a")
 *   launch {
 *     writeAction {}  // the globally published version changes
 *     version.set("b")
 *     assert(version.get() == "b")
 *   }.join()
 *   assert(version.get() == "a") // the changes in future versions are invisible to the captured version
 * }
 * ```
 */
@ApiStatus.Internal
@Debug.Renderer(childrenArray = "payloadMap.arrayOfPairs()", hasChildren = "payloadMap.size() != 0")
class VersionedPsiReference<T: Any> : PsiVersionCleanable {
  companion object {
    private val ACCESSOR = VarHandleWrapper.getFactory().create(VersionedPsiReference::class.java, "payloadMap", VersionedPayloadMap::class.java)
  }

  @Volatile
  private var payloadMap: VersionedPayloadMap = VersionedPayloadMap.empty()

  init {
    InternalPsiVersioning.PsiVersionRegistry.instance.registerCleanable(this)
  }

  /**
   * Update the existing value with [element] for the current version and returns the previously stored value.
   */
  fun set(element: T?): T? {
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    while (true) {
      val originalMap = getMap()
      val newMap = originalMap.insert(version, element)
      if (newMap == null) {
        return element
      }
      if (ACCESSOR.compareAndSet(this, originalMap, newMap)) {
        return originalMap.getValue(version)
      }
    }
  }

  /**
   * If a value is stored in this reference, [getOrPut] returns it.
   * Otherwise, computes [factory], atomically stores the result in this reference, and then returns the obtained result.
   */
  fun getOrPut(factory: () -> T): T {
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    val value = getVersioned(version)
    if (value != null) {
      return value
    }

    val newValue = factory()
    while (true) {
      val map = getMap()
      val result = map.getValue(version)
      if (result != null) {
        return result
      }
      val newMap = map.insert(version, newValue)
      if (newMap == null) {
        return newValue
      }
      if (ACCESSOR.compareAndSet(this, map, newMap)) {
        return newValue
      }
    }
  }

  /**
   * Copies the existing reference into a new [VersionedPsiReference]. The modifications performed on the previous reference are not reflected in the produced reference:
   * ```kotlin
   * val ref = VersionedPsiReference<String>("a")
   * val ref2 = ref.fork()
   * ref.set("b")
   * assert(ref.get() == "b")
   * assert(ref2.get() == "a")
   * ```
   */
  fun fork(): VersionedPsiReference<T> {
    val newReference = VersionedPsiReference<T>()
    newReference.payloadMap = payloadMap
    return newReference
  }


  fun get(): T? {
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    return getVersioned(version)
  }

  override fun toString(): String {
    return getMap().toString()
  }

  private fun getMap(): VersionedPayloadMap {
    return ACCESSOR.getVolatile(this) as VersionedPayloadMap
  }

  private fun VersionedPayloadMap.getValue(version: Long): T? {
    val payload = lowerBound(version) ?: return null
    @Suppress("UNCHECKED_CAST")
    return payload as T?
  }

  private fun getVersioned(version: Long): T? {
    return getMap().getValue(version)
  }

  override fun liveVersionChanged(minVersion: Long, liveVersions: Set<Long>) {
    val map = getMap()
    val newMap = map.cleanupStaleVersions(minVersion) ?: return
    ACCESSOR.compareAndSet(this, map, newMap)
  }

}
