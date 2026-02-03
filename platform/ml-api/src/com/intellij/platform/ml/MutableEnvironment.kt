// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.environment.Environment
import org.jetbrains.annotations.ApiStatus

/**
 * A mutable version of the [Environment], that can be extended.
 */
@ApiStatus.Internal
interface MutableEnvironment : Environment {
  /**
   * Adds new tier instance to the existing ones.
   * @throws IllegalArgumentException if there is already an instance of such [tier] in the environment.
   */
  fun <T : Any> putTierInstance(tier: Tier<T>, instance: T)
}

@ApiStatus.Internal
operator fun <T : Any> MutableEnvironment.set(tier: Tier<T>, instance: T) = putTierInstance(tier, instance)

@ApiStatus.Internal
fun <T : Any> MutableEnvironment.putTierInstance(tierInstance: TierInstance<T>) {
  this[tierInstance.tier] = tierInstance.instance
}
