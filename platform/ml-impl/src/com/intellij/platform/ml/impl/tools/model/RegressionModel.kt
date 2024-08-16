// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools.model

import com.intellij.internal.ml.DecisionFunction
import com.jetbrains.ml.*
import com.jetbrains.ml.model.MLModel
import org.jetbrains.annotations.ApiStatus


/**
 * A wrapper for using legacy [DecisionFunction] ML API`s [MLModel].
 */
@ApiStatus.Internal
open class RegressionModel private constructor(
  private val decisionFunction: DecisionFunction,
  private val modelUnits: Set<MLUnit<*>>,
  globalUnits: Set<MLUnit<*>>,
  private val featureSerialization: FeatureNameSerialization,
) : MLModel<Double> {
  constructor(
    decisionFunction: DecisionFunction,
    featureSerialization: FeatureNameSerialization,
    globalUnits: Set<MLUnit<*>>,
  ) : this(
    decisionFunction = decisionFunction,
    modelUnits = decisionFunction.featuresOrder.map { featureMapper ->
      featureSerialization.deserialize(featureMapper.featureName, globalUnits.associateBy { it.name }).first
    }.toSet(),
    globalUnits = globalUnits,
    featureSerialization = featureSerialization
  )

  override val knownFeatures: Map<MLUnit<*>, FeatureFilter> = createKnownFeatures(
    DecisionFunctionWrapper(decisionFunction, globalUnits, featureSerialization),
    modelUnits
  )

  override fun predict(taskUnits: List<MLUnitsMap>, contexts: List<Any?>, features: Map<MLUnit<*>, List<Feature>>): Double {
    val array = DoubleArray(decisionFunction.featuresOrder.size)
    val featurePerSerializedName: Map<String, Feature> = features
      .flatMap { (unit, unitFeatures) -> unitFeatures.map { unit to it } }
      .associate { (unit, feature) -> featureSerialization.serialize(unit, feature.declaration.name) to feature }

    require(features.keys == modelUnits) {
      "Given features units are ${features.keys}, but this model needs ${modelUnits}"
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
    fun serialize(mlUnit: MLUnit<*>, featureName: String): String

    fun deserialize(serializedFeatureName: String, unitsPerName: Map<String, MLUnit<*>>): Pair<MLUnit<*>, String>
  }

  object DefaultFeatureSerialization : FeatureNameSerialization {
    private const val SERIALIZED_FEATURE_SEPARATOR = '/'

    override fun serialize(mlUnit: MLUnit<*>, featureName: String): String {
      return mlUnit.name + SERIALIZED_FEATURE_SEPARATOR + featureName
    }

    override fun deserialize(serializedFeatureName: String, unitsPerName: Map<String, MLUnit<*>>): Pair<MLUnit<*>, String> {
      val indexOfLastSeparator = serializedFeatureName.indexOfLast { it == SERIALIZED_FEATURE_SEPARATOR }
      require(indexOfLastSeparator >= 0) { "Feature name '$serializedFeatureName' does not contain ML unit's name" }
      val unitName = serializedFeatureName.slice(0 until indexOfLastSeparator)
      val featureName = serializedFeatureName.slice(indexOfLastSeparator until serializedFeatureName.length)
      val featureUnit = requireNotNull(unitsPerName[unitName]) {
        """
          Serialized feature '$serializedFeatureName' has tier $unitName,
          but all available tiers are ${unitsPerName.keys}
        """.trimIndent()
      }
      return featureUnit to featureName
    }
  }

  private class DecisionFunctionWrapper(
    private val decisionFunction: DecisionFunction,
    globalUnits: Set<MLUnit<*>>,
    private val featureNameSerialization: FeatureNameSerialization,
  ) {
    private val availableUnitsPerName: Map<String, MLUnit<*>> = globalUnits.associateBy { it.name }

    val knownFeatures: Map<MLUnit<*>, Set<String>> = run {
      val knownFeaturesSerializedNames = decisionFunction.featuresOrder.map { it.featureName }.toSet()
      knownFeaturesSerializedNames
        .map { featureNameSerialization.deserialize(it, availableUnitsPerName) }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
    }

    fun getUnknownFeatures(unit: MLUnit<*>, featuresNames: Set<String>): Set<String> {
      val knownFeatures = knownFeatures[unit] ?: return featuresNames
      return featuresNames.filterNot { it in knownFeatures }.toSet()
    }
  }

  companion object {
    private fun createKnownFeatures(
      decisionFunction: DecisionFunctionWrapper,
      modelUnits: Set<MLUnit<*>>,
    ): Map<MLUnit<*>, FeatureFilter> {
      fun createFeatureSelector(unit: MLUnit<*>) = object : FeatureFilter {
        init {
          val knownFeatures = decisionFunction.knownFeatures
          knownFeatures.forEach { (unit, features) ->
            val nonConsistentlyKnownFeatures = decisionFunction.getUnknownFeatures(unit, features)
            require(nonConsistentlyKnownFeatures.isEmpty()) {
              "These features are known and unknown at the same time: $nonConsistentlyKnownFeatures"
            }
          }
        }

        override fun accept(featureDeclarations: Set<FeatureDeclaration<*>>): Set<FeatureDeclaration<*>> {
          val availableFeaturesPerName = featureDeclarations.associateBy { it.name }
          val availableFeaturesNames = featureDeclarations.map { it.name }.toSet()
          val unknownFeaturesNames = decisionFunction.getUnknownFeatures(unit, availableFeaturesNames)
          val knownAvailableFeaturesNames = availableFeaturesNames - unknownFeaturesNames
          val knownAvailableFeatures = knownAvailableFeaturesNames.map { availableFeaturesPerName.getValue(it) }.toSet()

          return knownAvailableFeatures
        }

        override fun accept(featureDeclaration: FeatureDeclaration<*>): Boolean {
          val unknown = decisionFunction.getUnknownFeatures(unit, setOf(featureDeclaration.name))
          return unknown.isEmpty()
        }
      }

      return modelUnits.associateWith { createFeatureSelector(it) }
    }
  }
}
