// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import org.jetbrains.annotations.ApiStatus

/**
 * A category of your application's objects that could be used to run the ML API.
 *
 * Tiers' main purpose is to be sources for features production, to pass more information to an ML model and to have the most precise
 * prediction.
 * Tiers are described per [TierDescriptor].
 * There are two categories of tiers when it comes to description:
 *
 *  - Main tiers:
 *  the ones that are declared in an [com.intellij.platform.ml.MLTask] and provided at the problem's application point,
 *  when [com.intellij.platform.ml.NestableMLSession.createNestedSession] is called.
 *  They exist in each session.
 *
 *  - Additional tiers: the ones that are declared in [com.intellij.platform.ml.LogDrivenModelInference].
 *  They are provided by various [com.intellij.platform.ml.environment.EnvironmentExtender]s occasionally
 *  when an [com.intellij.platform.ml.environment.ExtendedEnvironment] is crated,
 *  and they could be absent, as it was not able to satisfy extenders' requirements, or a tier instance was simply not present this time.
 *  So we can't rely on their existence.
 *
 * On the top of that, tiers could serve as helper objects for other user-defined objects,
 * as [TierDescriptor]s and [com.intellij.platform.ml.environment.EnvironmentExtender]s.
 * The listed interfaces are [TierRequester]s, which means that they require other tiers for proper functioning.
 * You could create additional [com.intellij.platform.ml.environment.EnvironmentExtender]s to create new tiers,
 * or to define other ways to instantiate existing tiers, but from other sources.
 */
@ApiStatus.Internal
abstract class Tier<T : Any> {
  /**
   * A unique name of a tier (among other tiers in your application).
   * Class name is used by default.
   */
  open val name: String
    get() = this.javaClass.simpleName

  override fun toString(): String = name
}

@ApiStatus.Internal
infix fun <T : Any> Tier<T>.with(instance: T) = TierInstance(this, instance)

/**
 * A helper class to type-safely handle pairs of [tier] and the corresponding [instance].
 */
@ApiStatus.Internal
data class TierInstance<T : Any>(val tier: Tier<T>, val instance: T)

typealias PerTier<T> = Map<Tier<*>, T>

typealias PerTierInstance<T> = Map<TierInstance<*>, T>

@ApiStatus.Internal
fun <T> Iterable<PerTier<T>>.joinByUniqueTier(): PerTier<T> {
  val joinedPerTier = mutableMapOf<Tier<*>, T>()

  this.forEach { perTierMapping ->
    perTierMapping.forEach { (tier, value) ->
      require(tier !in joinedPerTier)
      joinedPerTier[tier] = value
    }
  }

  return joinedPerTier
}

@ApiStatus.Internal
fun <T, CI : Iterable<T>, CO : MutableCollection<T>> Iterable<PerTier<CI>>.mergePerTier(createCollection: () -> CO): PerTier<CO> {
  val joinedPerTier = mutableMapOf<Tier<*>, CO>()
  for (perTierMapping in this) {
    for ((tier, anotherCollection) in perTierMapping) {
      val existingCollection = joinedPerTier[tier] ?: emptyList()
      require(anotherCollection.all { it !in existingCollection })
      joinedPerTier.computeIfAbsent(tier) { createCollection() }.addAll(anotherCollection)
    }
  }
  return joinedPerTier
}
