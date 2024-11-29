// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.util.NotNullizer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

/**
 * # Why cache?
 * String equality on each access adds up, we want reference equality by Class instance as a key.
 *
 * # Cache consistency
 * [cache] contains the subset of whatever it is in [holders].
 */
internal class InstanceContainerState private constructor(
  @JvmField val holders: InstanceHolders,
  private var cache: PersistentMap<Class<*>, InstanceHolder>,
) {

  /**
   * Creates a state with empty cache.
   */
  constructor(holders: InstanceHolders) : this(holders, persistentHashMapOf())

  fun getByName(keyClassName: String): InstanceHolder? = holders[keyClassName]

  @Suppress("UNCHECKED_CAST")
  fun getByClass(keyClass: Class<*>): InstanceHolder? {
    var cache = cacheHandle.getVolatile(this) as PersistentMap<Any, Any>
    while (true) {
      val cached = cache[keyClass]
      if (cached != null) {
        return notNullizer.nullize(cached) as InstanceHolder?
      }

      val holder = getByName(keyClass.name)
      val newValue = cache.put(keyClass, notNullizer.notNullize(holder))
      val witness = cacheHandle.compareAndExchange(this, cache, newValue)
      if (witness === cache) {
        return holder
      }
      cache = witness as PersistentMap<Any, Any>
    }
  }

  fun replaceByName(keyClassName: String, holder: InstanceHolder?): InstanceContainerState {
    /**
     * # Why cache is cleared?
     * To keep it consistent with new [holders].
     * Alternatively we'd have to go through the whole map, and update [Class] keys which match [keyClassName].
     * Since this is called when container is being initialized or disposed, it's okay to not waste time on maintaining cache.
     */
    val newHolders = if (holder == null) {
      holders.remove(keyClassName)
    }
    else {
      holders.put(keyClassName, holder)
    }
    return InstanceContainerState(newHolders)
  }

  fun replaceByClass(keyClass: Class<*>, holder: InstanceHolder): InstanceContainerState {
    /**
     * # Why cache is not cleared
     * It's possible to keep the existing [cache] because it's populated with the same [holder], which keeps the cache consistency.
     * This is called on dynamic instance registration, as opposed to [replaceByName],
     * and we want to avoid dropping cache in the middle of container lifecycle.
     *
     * # Why race is ok
     * It's racy to read [cache] since it might be modified after the current [InstanceContainerState] instance was read from the field,
     * but it's okay because of cache consistency.
     * At worst, we might lose some published cached key, but it will be re-cached in the next published [InstanceContainerState] instance.
     */
    return InstanceContainerState(
      holders = holders.put(keyClass.name, holder),
      cache = cache.put(keyClass, holder),
    )
  }

  private companion object {

    @JvmField
    val notNullizer = NotNullizer("InstanceContainerState.Nothing")

    @JvmField
    val cacheHandle: VarHandle = MethodHandles.lookup()
      .findVarHandle(InstanceContainerState::class.java, "cache", PersistentMap::class.java)
  }
}
