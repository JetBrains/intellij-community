// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.environment.get
import org.jetbrains.annotations.ApiStatus


/**
 * An object that contains tier instances, and it could be extended.
 */
@ApiStatus.Internal
class TierInstanceStorage : MutableEnvironment {
  private val instances: MutableMap<Tier<*>, Any> = mutableMapOf()

  override val tiers: Set<Tier<*>>
    get() = instances.keys

  override val tierInstances: Set<TierInstance<*>>
    get() = instances
      .map { (tier, tierInstance) -> tier withUnsafe tierInstance }
      .toSet()

  private infix fun <T : Any, P : Any> Tier<T>.withUnsafe(value: P): TierInstance<T> {
    @Suppress("UNCHECKED_CAST")
    return this.with(value as T)
  }

  override fun <T : Any> getInstance(tier: Tier<T>): T {
    val tierInstance = instances[tier]
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(tierInstance) as T
  }

  override fun <T : Any> putTierInstance(tier: Tier<T>, instance: T) {
    require(tier !in this) {
      "Tier $tier is already registered in the storage. Old value: '${instances[tier]}', new value: '$instance'"
    }
    instances[tier] = instance
  }

  companion object {
    fun copyOf(environment: Environment): TierInstanceStorage {
      val storage = TierInstanceStorage()
      fun <T : Any> putInstance(tier: Tier<T>) {
        storage[tier] = environment[tier]
      }
      environment.tiers.forEach { putInstance(it) }
      return storage
    }

    fun joined(environments: Iterable<Environment>): Environment {
      val commonStorage = TierInstanceStorage()

      fun <T : Any> putCapturingType(tier: Tier<T>, environment: Environment) {
        commonStorage[tier] = environment[tier]
      }

      environments.forEach { environment ->
        environment.tiers.forEach { tier ->
          putCapturingType(tier, environment)
        }
      }

      return commonStorage
    }
  }
}
