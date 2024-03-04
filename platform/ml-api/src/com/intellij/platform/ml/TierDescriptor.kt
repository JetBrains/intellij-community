// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Provides features for a particular [tier].
 *
 * It is also a [TierRequester], which implies that [additionallyRequiredTiers] could be defined, in
 * case additional objects are required to describe the [tier]'s instance.
 * If so, an attempt will be made to create an [com.intellij.platform.ml.impl.environment.ExtendedEnvironment],
 * and the extended environment that contains the described [tier] and [additionallyRequiredTiers]
 * will be passed to the [describe] function.
 *
 * @see ObsoleteTierDescriptor if you are looking for an opportunity to smoothly transfer your old feature providers to this API.
 */
@ApiStatus.Internal
interface TierDescriptor : TierRequester {
  /**
   * The tier, that this descriptor is describing (giving features to).
   */
  val tier: Tier<*>

  val descriptionPolicy: DescriptionPolicy

  /**
   * All features that could ever be used in the declaration.
   *
   * _Important_: Do not change features' names.
   * Names serve as features identifiers, and they are used to pass to ML models.
   */
  val descriptionDeclaration: Set<FeatureDeclaration<*>>

  /**
   * Computes [tier]'s features from with the features from [descriptionDeclaration].
   *
   * If [additionallyRequiredTiers] could not be satisfied, then this descriptor is not called at all.
   * Be aware that _each_ feature given in [descriptionDeclaration] must be calculated here.
   * If it is possible that a feature is not computable withing particular circumstances, then
   * you could declare your feature as nullable: [FeatureDeclaration.nullable].
   *
   * @param environment Contains [tier] and [additionallyRequiredTiers].
   * @param usefulFeaturesFilter Accepts features, that could make any difference to compute this time.
   * A feature is considered to be useful if an ML model is aware of the feature,
   * or it is explicitly said that "ML model is not aware of this feature, but it must be logged"
   * (@see [com.intellij.platform.ml.impl.LogDrivenModelInference]).
   *
   * @throws IllegalArgumentException If a feature is missing: it is accepted by [usefulFeaturesFilter]
   * and it is declared, but not present in the result.
   * @throws IllegalArgumentException If a redundant feature was computed, that was not declared.
   */
  suspend fun describe(environment: Environment, usefulFeaturesFilter: FeatureFilter): Set<Feature>

  /**
   * Declares a requirement and ensures, that [describe] will be called if and only if
   * the [environment] will contain both [tier] and [additionallyRequiredTiers].
   */
  override val requiredTiers: Set<Tier<*>>
    get() = additionallyRequiredTiers + tier

  /**
   * Tiers that are required additionally to perform description.
   *
   * Such tiers' instances could be created via existing or additionally
   * created [EnvironmentExtender]s.
   */
  val additionallyRequiredTiers: Set<Tier<*>>
    get() = emptySet()

  /**
   * Tells if the descriptor could generate any useful features at all.
   * @param usefulFeaturesFilter Accepts features, that make sense to calculate at the time of this invocation.
   * If the filter does not accept a feature, it means that its computation will not make any difference.
   */
  fun couldBeUseful(usefulFeaturesFilter: FeatureFilter): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<TierDescriptor> = ExtensionPointName.create("com.intellij.platform.ml.descriptor")
  }

  abstract class Default(final override val tier: Tier<*>) : TierDescriptor {
    override val additionallyRequiredTiers: Set<Tier<*>>
      get() = emptySet()

    override val descriptionPolicy: DescriptionPolicy
      get() = DescriptionPolicy(false, false)

    override fun couldBeUseful(usefulFeaturesFilter: FeatureFilter): Boolean {
      return descriptionDeclaration.any { usefulFeaturesFilter.accept(it) }
    }
  }
}

@ApiStatus.Internal
data class DescriptionPolicy(
  /**
   * Tolerate redundant computations, because the computations are "light-weight".
   * When an ML model is not aware of the feature, it's still allowing computing it in [TierDescriptor.describe].
   * Otherwise, an exception will be thrown
   */
  val tolerateRedundantDescription: Boolean,

  /**
   * When the feature is nullable, allow not putting the 'null' explicitly to the result set of [TierDescriptor.describe].
   */
  val putNullImplicitly: Boolean
)
