// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.platform.ml.LevelTiers
import com.intellij.platform.ml.MLModel
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.Tier
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureSelector
import org.jetbrains.annotations.ApiStatus

/**
 * A wrapper for using legacy [DecisionFunction] as the new API`s [MLModel].
 */
@ApiStatus.Internal
open class RegressionModel private constructor(
  private val decisionFunction: DecisionFunction,
  private val featuresTiers: Set<Tier<*>>,
  availableTiers: Set<Tier<*>>,
  private val featureSerialization: FeatureNameSerialization
) : MLModel<Double> {
  constructor(decisionFunction: DecisionFunction,
              featureSerialization: FeatureNameSerialization,
              sessionTiers: List<LevelTiers>) : this(
    decisionFunction = decisionFunction,
    featuresTiers = decisionFunction.featuresOrder.map {
      featureSerialization.deserialize(it.featureName, sessionTiers.flatten().associateBy { it.name }).first
    }.toSet(),
    availableTiers = sessionTiers.flatten(),
    featureSerialization = featureSerialization
  )

  override val knownFeatures: PerTier<FeatureSelector> = createFeatureSelectors(
    DecisionFunctionWrapper(decisionFunction, availableTiers, featureSerialization),
    featuresTiers
  )

  override fun predict(callParameters: List<Environment>, features: PerTier<Set<Feature>>): Double {
    val array = DoubleArray(decisionFunction.featuresOrder.size)
    val featurePerSerializedName = features
      .flatMap { (tier, tierFeatures) -> tierFeatures.map { tier to it } }
      .associate { (tier, feature) -> featureSerialization.serialize(tier, feature.declaration.name) to feature }

    require(features.keys == featuresTiers) {
      "Given features tiers are ${features.keys}, but this model needs ${featuresTiers}"
    }

    for (featureI in decisionFunction.featuresOrder.indices) {
      val featureMapper = decisionFunction.featuresOrder[featureI]
      val featureSerializedName = featureMapper.featureName
      val featureValue = featureMapper.asArrayValue(featurePerSerializedName[featureSerializedName]?.value)
      array[featureI] = featureValue
    }

    return decisionFunction.predict(array)
  }

  interface FeatureNameSerialization {
    fun serialize(tier: Tier<*>, featureName: String): String

    fun deserialize(serializedFeatureName: String, availableTiersPerName: Map<String, Tier<*>>): Pair<Tier<*>, String>
  }

  @Suppress("UNUSED")
  object DefaultFeatureSerialization : FeatureNameSerialization {
    private val SERIALIZED_FEATURE_SEPARATOR = '/'

    override fun serialize(tier: Tier<*>, featureName: String): String {
      return "${tier.name}${SERIALIZED_FEATURE_SEPARATOR}${featureName}"
    }

    override fun deserialize(serializedFeatureName: String, availableTiersPerName: Map<String, Tier<*>>): Pair<Tier<*>, String> {
      val indexOfLastSeparator = serializedFeatureName.indexOfLast { it == SERIALIZED_FEATURE_SEPARATOR }
      require(indexOfLastSeparator >= 0) { "Feature name '$serializedFeatureName' does not contain tier's name" }
      val featureTierName = serializedFeatureName.slice(0 until indexOfLastSeparator)
      val featureName = serializedFeatureName.slice(indexOfLastSeparator until serializedFeatureName.length)
      val featureTier = requireNotNull(availableTiersPerName[featureTierName]) {
        """
          Serialized feature '$serializedFeatureName' has tier $featureTierName,
          but all available tiers are ${availableTiersPerName.keys}
        """.trimIndent()
      }
      return featureTier to featureName
    }
  }

  class SelectionMissingFeatures(
    selectedFeatures: Set<FeatureDeclaration<*>>,
    missingFeatures: Set<String>
  ) : FeatureSelector.Selection.Incomplete(selectedFeatures) {
    override val details: String = "Regression model requires more features to run. " +
                                   "Missing: $missingFeatures, " +
                                   "Has: $selectedFeatures"
  }

  private class DecisionFunctionWrapper(
    private val decisionFunction: DecisionFunction,
    private val availableTiers: Set<Tier<*>>,
    private val featureNameSerialization: FeatureNameSerialization
  ) {
    private val availableTiersPerName: Map<String, Tier<*>> = availableTiers.associateBy { it.name }

    val knownFeatures: PerTier<Set<String>> = run {
      val knownFeaturesSerializedNames = decisionFunction.featuresOrder.map { it.featureName }.toSet()
      knownFeaturesSerializedNames
        .map { featureNameSerialization.deserialize(it, availableTiersPerName) }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
    }

    val requiredFeaturesPerTier: PerTier<Set<String>> = run {
      val availableTiersPerName = availableTiers.associateBy { it.name }
      val requiredFeaturesSerializedNames = decisionFunction.requiredFeatures.filterNotNull().toSet()
      requiredFeaturesSerializedNames
        .map { serializedFeatureName -> featureNameSerialization.deserialize(serializedFeatureName, availableTiersPerName) }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
    }

    fun getUnknownFeatures(tier: Tier<*>, featuresNames: Set<String>): Set<String> {
      val knownTierFeatures = knownFeatures[tier] ?: return featuresNames
      return featuresNames.filterNot { it in knownTierFeatures }.toSet()
    }
  }

  companion object {
    private fun createFeatureSelectors(decisionFunction: DecisionFunctionWrapper,
                                       featuresTiers: Set<Tier<*>>): PerTier<FeatureSelector> {
      val requiredFeaturesPerTier = decisionFunction.requiredFeaturesPerTier

      fun createFeatureSelector(tier: Tier<*>) = object : FeatureSelector {
        init {
          val knownFeatures = decisionFunction.knownFeatures
          knownFeatures.forEach { (tier, tierFeatures) ->
            val nonConsistentlyKnownFeatures = decisionFunction.getUnknownFeatures(tier, tierFeatures)
            require(nonConsistentlyKnownFeatures.isEmpty()) {
              "These features are known and unknown at the same time: $nonConsistentlyKnownFeatures"
            }
          }
        }

        override fun select(availableFeatures: Set<FeatureDeclaration<*>>): FeatureSelector.Selection {
          val availableFeaturesPerName = availableFeatures.associateBy { it.name }
          val availableFeaturesNames = availableFeatures.map { it.name }.toSet()
          val unknownFeaturesNames = decisionFunction.getUnknownFeatures(tier, availableFeaturesNames)
          val knownAvailableFeaturesNames = availableFeaturesNames - unknownFeaturesNames
          val knownAvailableFeatures = knownAvailableFeaturesNames.map { availableFeaturesPerName.getValue(it) }.toSet()
          val requiredFeaturesNames = requiredFeaturesPerTier[tier] ?: emptySet()

          return if (availableFeaturesNames.containsAll(requiredFeaturesNames))
            FeatureSelector.Selection.Complete(knownAvailableFeatures)
          else
            SelectionMissingFeatures(knownAvailableFeatures, requiredFeaturesNames - availableFeaturesNames)
        }

        override fun select(featureDeclaration: FeatureDeclaration<*>): Boolean {
          return decisionFunction.getUnknownFeatures(tier, setOf(featureDeclaration.name)).isEmpty()
        }
      }

      return featuresTiers.associateWith { createFeatureSelector(it) }
    }
  }
}

private fun List<LevelTiers>.flatten(): Set<Tier<*>> {
  return this.flatMap { it.main + it.additional }.toSet()
}
