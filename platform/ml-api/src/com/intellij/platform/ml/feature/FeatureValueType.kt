// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.feature

import com.intellij.platform.ml.logs.schema.EventField
import org.jetbrains.annotations.ApiStatus

/**
 * A type of [Feature].
 *
 * If you need to use another type of feature to use in your ML model or in analysis,
 * consider contacting the ML API developers.
 */
@ApiStatus.Internal
sealed class FeatureValueType<T> {
  /**
   * Called on every ml session, when the feature object should be created
   */
  abstract fun instantiate(name: String, value: T, descriptionProvider: () -> String): Feature

  data class Nullable<T>(val baseType: FeatureValueType<T>) : FeatureValueType<T?>() {
    override fun instantiate(name: String, value: T?, descriptionProvider: () -> String): Feature {
      return Feature.Nullable(name, value, baseType, descriptionProvider)
    }
  }

  data class Enum<T : kotlin.Enum<*>>(val enumClass: java.lang.Class<T>) : FeatureValueType<T>() {
    override fun instantiate(name: String, value: T, descriptionProvider: () -> String): Feature {
      return Feature.Enum(name, value, descriptionProvider)
    }
  }

  data class Categorical(val possibleValues: Set<String>) : FeatureValueType<String>() {
    override fun instantiate(name: String, value: String, descriptionProvider: () -> String): Feature {
      require(value in possibleValues) {
        val caseNonMatchingValue = possibleValues.find { it.equals(name, ignoreCase = true) }
        "Feature $name cannot be assigned to value $value," +
        "all possible values are $possibleValues. " +
        "Possible match (but case does not match): $caseNonMatchingValue"
      }
      return Feature.Categorical(name, value, possibleValues)
    }
  }

  object Int : FeatureValueType<kotlin.Int>() {
    override fun instantiate(name: String, value: kotlin.Int, descriptionProvider: () -> String): Feature {
      return Feature.Int(name, value, descriptionProvider)
    }
  }

  object Double : FeatureValueType<kotlin.Double>() {
    override fun instantiate(name: String, value: kotlin.Double, descriptionProvider: () -> String): Feature {
      return Feature.Double(name, value, descriptionProvider)
    }
  }

  object Float : FeatureValueType<kotlin.Float>() {
    override fun instantiate(name: String, value: kotlin.Float, descriptionProvider: () -> String): Feature {
      return Feature.Float(name, value, descriptionProvider)
    }
  }

  object Long : FeatureValueType<kotlin.Long>() {
    override fun instantiate(name: String, value: kotlin.Long, descriptionProvider: () -> String): Feature {
      return Feature.Long(name, value, descriptionProvider)
    }
  }

  object Class : FeatureValueType<java.lang.Class<*>>() {
    override fun instantiate(name: String, value: java.lang.Class<*>, descriptionProvider: () -> String): Feature {
      return Feature.Class(name, value, descriptionProvider)
    }
  }

  object Boolean : FeatureValueType<kotlin.Boolean>() {
    override fun instantiate(name: String, value: kotlin.Boolean, descriptionProvider: () -> String): Feature {
      return Feature.Boolean(name, value, descriptionProvider)
    }
  }

  abstract class Custom<T>(val eventFieldBuilder: (String, () -> String) -> EventField<T>) : FeatureValueType<T>()

  override fun toString(): String = this.javaClass.simpleName
}
