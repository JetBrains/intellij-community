// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class Feature {
  abstract val name: String
  abstract fun createMapper(suffix: String?): FeatureMapper

  companion object {
    const val UNDEFINED: String = "UNDEFINED"
  }
}

internal typealias ValueMapping = Pair<String, Double>

data class BinaryFeature(override val name: String,
                         val firstValueMapping: ValueMapping,
                         val secondValueMapping: ValueMapping,
                         val defaultValue: Double,
                         val allowUndefined: Boolean) : Feature() {

  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, allowUndefined)
      null -> BinaryMapper()
      else -> throw InconsistentMetadataException("Unexpected binary feature suffix: $suffix (feature '$name')")
    }
  }

  private inner class BinaryMapper : FeatureMapper {
    val firstMappingLowercase = ValueMapping(firstValueMapping.first.lowercase(), firstValueMapping.second)
    val secondMappingLowercase = ValueMapping(secondValueMapping.first.lowercase(), secondValueMapping.second)
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      return when (value.toString().lowercase()) {
        firstMappingLowercase.first -> firstMappingLowercase.second
        secondMappingLowercase.first -> secondMappingLowercase.second
        else -> defaultValue
      }
    }
  }
}

data class FloatFeature(override val name: String, val defaultValue: Double, val allowUndefined: Boolean) : Feature() {
  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, allowUndefined)
      null -> FloatMapper()
      else -> throw InconsistentMetadataException("Unexpected float feature suffix: $suffix (feature '$name')")
    }
  }

  private inner class FloatMapper : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      return value.asDouble() ?: defaultValue
    }

    private fun Any?.asDouble(): Double? {
      if (this is Number) return this.toDouble()
      return this.toString().toDoubleOrNull()
    }
  }
}


data class CategoricalFeature(override val name: String, val categories: Set<String>) : Feature() {
  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, UNDEFINED in categories)
      OTHER -> otherCategoryMapper()
      null -> throw InconsistentMetadataException("Categorical feature usage must have suffix")
      else -> if (suffix in categories) CategoryMapper(suffix.lowercase())
      else throw InconsistentMetadataException("Unknown category '$suffix' of categorical feature '$name'")
    }
  }

  private fun otherCategoryMapper(): FeatureMapper {
    if (OTHER !in categories) throw InconsistentMetadataException("Feature $name does not allow other category")
    return OtherCategoryMapper(name, categories.minus(listOf(UNDEFINED, OTHER)))
  }

  private class OtherCategoryMapper(private val name: String, private val knownCategories: Set<String>) : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double = if (value == null || value.toString() in knownCategories) 0.0 else 1.0
  }

  private inner class CategoryMapper(private val category: String) : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      if (value == null) {
        return 0.0
      }

      return if (value.toString().lowercase() == category) 1.0 else 0.0
    }
  }

  companion object {
    const val OTHER: String = "OTHER"
  }
}

private class UndefinedMapper private constructor(private val featureName: String) : FeatureMapper {
  override fun getFeatureName(): String = featureName

  override fun asArrayValue(value: Any?): Double = if (value == null) 1.0 else 0.0

  companion object {
    fun checkAndCreate(featureName: String, canUseUndefined: Boolean): FeatureMapper {
      if (!canUseUndefined) {
        throw InconsistentMetadataException("Feature '$featureName' does not handle undefined values")
      }

      return UndefinedMapper(featureName)
    }
  }
}

data class BagOfWordsFeature(override val name: String, val words: Set<String>, val splitterDescriptor: SplitterDescriptor?) : Feature() {
  data class SplitterDescriptor(val withStemming: Boolean, val toLowerCase: Boolean)

  private val splitter = splitterDescriptor?.let { descriptor ->
    WordsSplitter.Builder()
      .apply { if (descriptor.toLowerCase) toLowerCase() }
      .apply { if (descriptor.withStemming) withStemming() }
      .build()
  }

  private val wordsCache = object : LinkedHashMap<String, List<String>>() {
    override fun removeEldestEntry(eldest: Map.Entry<String, List<String>>): Boolean {
      return size > MAX_CACHE_SIZE
    }
  }

  override fun createMapper(suffix: String?): FeatureMapper {
    return when (suffix) {
      UNDEFINED -> UndefinedMapper.checkAndCreate(name, UNDEFINED in words)
      null -> throw InconsistentMetadataException("Bag of words feature usage must have suffix")
      else -> if (suffix in words) BagOfWordsMapper(suffix)
      else throw InconsistentMetadataException("Unknown word '$suffix' of Bag of words feature '$name'")
    }
  }

  private fun split(value: String): List<String> =
    wordsCache.computeIfAbsent(value) { splitter?.split(value) ?: listOf(value) }

  private inner class BagOfWordsMapper(private val word: String) : FeatureMapper {
    override fun getFeatureName(): String = name

    override fun asArrayValue(value: Any?): Double {
      if (value == null) {
        return 0.0
      }
      val words = when (value) {
        is String -> split(value)
        is List<*> -> value.filterNotNull().flatMap { split(it.toString()) }
        else -> emptyList()
      }

      return if (word in words) 1.0 else 0.0
    }
  }

  companion object {
    const val UNDEFINED: String = "_UNDEFINED_"
    const val MAX_CACHE_SIZE: Int = 10
  }
}