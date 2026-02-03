// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.ScopeEnvironment.Companion.accessibleSafelyBy
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureFilter
import org.jetbrains.annotations.ApiStatus

/**
 * Computes tiers descriptions, calling [TierDescriptor.describe],
 * or caching the descriptions.
 */
@ApiStatus.Internal
interface DescriptionComputer {
  /**
   *
   * @param tier The tier that all is being described.
   * @param descriptors All relevant descriptors, that can be called within [environment]
   * (it is guaranteed that they all describe [tier] and their requirements are fulfilled by [environment]).
   * @param environment An environment, that contains all tiers required to run any of the [descriptors].
   * @param usefulFeaturesFilter Accepts features, that are meaningful to compute. If the filter does not
   * accept a feature, its computation will not make any difference later.
   */
  suspend fun computeDescription(
    tier: Tier<*>,
    descriptors: List<TierDescriptor>,
    environment: Environment,
    usefulFeaturesFilter: FeatureFilter,
  ): Map<TierDescriptor, Set<Feature>>
}

/**
 * Does not cache descriptors, primitively computes all descriptors all over again each time.
 */
@ApiStatus.Internal
object StateFreeDescriptionComputer : DescriptionComputer {
  override suspend fun computeDescription(tier: Tier<*>,
                                          descriptors: List<TierDescriptor>,
                                          environment: Environment,
                                          usefulFeaturesFilter: FeatureFilter): Map<TierDescriptor, Set<Feature>> {
    return descriptors.associateWith { descriptor ->
      require(descriptor.tier == tier)
      require(descriptor.requiredTiers.all { it in environment.tiers })
      descriptor.describe(environment.accessibleSafelyBy(descriptor), usefulFeaturesFilter)
    }
  }
}
