// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.internal.ml

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FeaturesInfo(
  override val knownFeatures: Set<String>,
  override val binaryFeatures: List<BinaryFeature>,
  override val floatFeatures: List<FloatFeature>,
  override val categoricalFeatures: List<CategoricalFeature>,
  override val featuresOrder: Array<FeatureMapper>,
  override val version: String?,
) : ModelMetadata {
  companion object {
    private const val DEFAULT: String = "default"
    private const val USE_UNDEFINED: String = "use_undefined"

    private fun List<String>.withSafeWeighers(): Set<String> {
      val result = this.toMutableSet()
      result.add("prox_directoryType")
      result.add("kt_prox_directoryType")
      result.add("kotlin.unwantedElement")
      return result
    }

    fun buildInfo(reader: ModelMetadataReader): FeaturesInfo {
      val knownFeatures = Json.decodeFromString<List<String>>(reader.allKnown()).withSafeWeighers()

      val binaryFactors: List<BinaryFeature> = Json.decodeFromString<Map<String, Map<String, JsonElement>>>(reader.binaryFeatures())
        .map { binary(it.key, it.value) }
      val doubleFactors = Json.decodeFromString<Map<String, Map<String, JsonElement>>>(reader.floatFeatures())
        .map { float(it.key, it.value) }
      val categoricalFactors = Json.decodeFromString<Map<String, List<String>>>(reader.categoricalFeatures())
        .map { categorical(it.key, it.value) }

      val order = reader.featureOrderDirect()

      val featuresIndex = buildFeaturesIndex(binaryFactors, doubleFactors, categoricalFactors)
      return FeaturesInfo(knownFeatures, binaryFactors, doubleFactors, categoricalFactors, buildMappers(featuresIndex, order),
                          reader.extractVersion())
    }

    fun binary(name: String, description: Map<String, JsonElement>): BinaryFeature {
      val (first, second) = extractBinaryValuesMappings(description)
      val default = extractDefaultValue(name, description)
      return BinaryFeature(name, first, second, default, allowUndefined(description))
    }

    fun float(name: String, description: Map<String, JsonElement>): FloatFeature {
      val default = extractDefaultValue(name, description)
      return FloatFeature(name, default, allowUndefined(description))
    }

    fun categorical(name: String, categories: List<String>): CategoricalFeature {
      return CategoricalFeature(name, categories.toSet())
    }

    fun buildFeaturesIndex(vararg featureGroups: List<Feature>): Map<String, Feature> {
      fun <T : Feature> MutableMap<String, Feature>.addFeatures(features: List<T>): MutableMap<String, Feature> {
        for (feature in features) {
          if (feature.name in keys) throw InconsistentMetadataException(
            "Ambiguous feature description '${feature.name}': $feature and ${this[feature.name]}")
          this[feature.name] = feature
        }

        return this
      }
      val index = mutableMapOf<String, Feature>()
      for (features in featureGroups) {
        index.addFeatures(features)
      }
      return index
    }

    private fun allowUndefined(description: Map<String, Any>): Boolean {
      val value = description[USE_UNDEFINED]
      if (value is Boolean) {
        return value
      }

      return true
    }

    private fun extractDefaultValue(name: String, description: Map<String, JsonElement>): Double {
      val value = description.get(DEFAULT) ?: throw InconsistentMetadataException("Default value not found. Feature name: $name")
      try {
        return value.jsonPrimitive.double
      }
      catch (_: IllegalArgumentException) {
        throw InconsistentMetadataException("Default value must be double(name=$name, value=$value")
      }
    }

    private fun extractBinaryValuesMappings(description: Map<String, JsonElement>): Pair<ValueMapping, ValueMapping> {
      val result = mutableListOf<ValueMapping>()
      for ((name, value) in description) {
        if (name == DEFAULT || name == USE_UNDEFINED) {
          continue
        }
        val mappedValue = try {
          value.jsonPrimitive.double
        }
        catch (e: IllegalArgumentException) {
          throw InconsistentMetadataException("Mapped value for binary feature should be double (value=$value)")
        }
        result.add(name to mappedValue)
      }

      assert(result.size == 2) { "Binary feature must contains 2 values, but found $result" }
      result.sortBy { it.first }
      return Pair(result[0], result[1])
    }

    fun buildMappers(features: Map<String, Feature>, order: List<String>): Array<FeatureMapper> {
      val mappers = mutableListOf<FeatureMapper>()
      for (arrayFeatureName in order) {
        val name = arrayFeatureName.substringBefore('=')
        val suffix = arrayFeatureName.indexOf('=').let { if (it == -1) null else arrayFeatureName.substring(it + 1) }
        val mapper = features[name]?.createMapper(suffix) ?: throw InconsistentMetadataException(
          "Unexpected feature name in array: $arrayFeatureName")
        mappers.add(mapper)
      }

      return mappers.toTypedArray()
    }
  }
}