// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.environment

import com.intellij.platform.ml.Tier
import com.intellij.platform.ml.TierInstance
import com.intellij.platform.ml.TierInstanceStorage
import com.intellij.platform.ml.set
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an environment that is being assembled to be described by [com.intellij.platform.ml.TierDescriptor]s,
 * to acquire a new ML Model, or for another reason.
 */
@ApiStatus.Internal
interface Environment {
  /**
   * The set of tiers, that the environment contains.
   */
  val tiers: Set<Tier<*>>

  /**
   * @return an instance, that corresponds to the given [tier].
   * @throws IllegalArgumentException if the [tier] is not present
   */
  fun <T : Any> getInstance(tier: Tier<T>): T

  /**
   * The set of tier instances that are present in the environment.
   */
  val tierInstances: Set<TierInstance<*>>
    get() {
      return tiers.map { this.getTierInstance(it) }.toSet()
    }

  /**
   * @return a tier instance wrapped into [TierInstance] class.
   * @throws IllegalArgumentException if the tier is not present.
   */
  fun <T : Any> getTierInstance(tier: Tier<T>) = TierInstance(tier, this[tier])

  /**
   * @return if the tier is present in the environment
   */
  operator fun contains(tier: Tier<*>): Boolean = tier in this.tiers

  companion object {
    /**
     * @return An environment that contains all tiers of all given environments.
     * @throws IllegalArgumentException if there is a tier that is present in more than two
     * environments.
     */
    fun joined(environments: Iterable<Environment>): Environment = TierInstanceStorage.joined(environments)

    fun joined(vararg environments: Environment): Environment = TierInstanceStorage.joined(environments.toList())

    fun empty(): Environment = joined(emptySet())

    /**
     * Builds an environment that contains all the given tiers.
     * @throws IllegalArgumentException if there is more than one instance of a particular tier.
     */
    fun of(entries: Iterable<TierInstance<*>>): Environment {
      val storage = TierInstanceStorage()
      fun <T : Any> putToStorage(tierInstance: TierInstance<T>) {
        storage[tierInstance.tier] = tierInstance.instance
      }
      entries.forEach { putToStorage(it) }
      return storage
    }

    fun of(vararg entries: TierInstance<*>): Environment = of(entries.toList())
  }
}

@ApiStatus.Internal
operator fun <T : Any> Environment.get(tier: Tier<T>): T = getInstance(tier)
