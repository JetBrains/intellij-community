// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.environment.Environment
import org.jetbrains.annotations.ApiStatus


/**
 * An environment, that restricts access to the [baseEnvironment] with the [scope].
 * It is created before passing an [Environment] to a [TierDescriptor], so it will
 * not be accessing non-declared tiers.
 */
@ApiStatus.Internal
class ScopeEnvironment private constructor(
  private val baseEnvironment: Environment,
  private val scope: Set<Tier<*>>
) : Environment {
  override val tiers: Set<Tier<*>> = scope

  override fun <T : Any> getInstance(tier: Tier<T>): T {
    require(tier in scope) { "$tier was not supposed to be accessed, allowed scope: ${scope}" }
    return baseEnvironment.getInstance(tier)
  }

  companion object {
    fun Environment.restrictedBy(scope: Set<Tier<*>>) = ScopeEnvironment(this, scope.intersect(this.tiers))

    fun Environment.narrowedTo(scope: Set<Tier<*>>): ScopeEnvironment {
      require(scope.all { it in this })
      return ScopeEnvironment(this, scope)
    }

    fun Environment.accessibleSafelyByOrNull(requester: TierRequester): ScopeEnvironment? {
      if (requester.requiredTiers.any { it !in this }) {
        return null
      }
      return this.accessibleSafelyBy(requester)
    }

    fun Environment.accessibleSafelyBy(requester: TierRequester): ScopeEnvironment = this.narrowedTo(requester.requiredTiers)
  }
}
