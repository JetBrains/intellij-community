// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import org.jetbrains.annotations.ApiStatus

/**
 * A type of [Feature].
 *
 * If you need to use another type of feature to use in your ML model or in analysis,
 * consider contacting the ML API developers.
 */
@ApiStatus.Internal
sealed class FeatureValueType<T> {
  abstract fun instantiate(name: String, value: T): Feature

  data class Nullable<T>(val baseType: FeatureValueType<T>) : FeatureValueType<T?>() {
    override fun instantiate(name: String, value: T?): Feature {
      return Feature.Nullable(name, value, baseType)
    }
  }

  data class Enum<T : kotlin.Enum<*>>(val enumClass: java.lang.Class<T>) : FeatureValueType<T>() {
    override fun instantiate(name: String, value: T): Feature {
      return Feature.Enum(name, value)
    }
  }

  data class Categorical(val possibleValues: Set<String>) : FeatureValueType<String>() {
    override fun instantiate(name: String, value: String): Feature {
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
    override fun instantiate(name: String, value: kotlin.Int): Feature {
      return Feature.Int(name, value)
    }
  }

  object Double : FeatureValueType<kotlin.Double>() {
    override fun instantiate(name: String, value: kotlin.Double): Feature {
      return Feature.Double(name, value)
    }
  }

  object Float : FeatureValueType<kotlin.Float>() {
    override fun instantiate(name: String, value: kotlin.Float): Feature {
      return Feature.Float(name, value)
    }
  }

  object Long : FeatureValueType<kotlin.Long>() {
    override fun instantiate(name: String, value: kotlin.Long): Feature {
      return Feature.Long(name, value)
    }
  }

  object Class : FeatureValueType<java.lang.Class<*>>() {
    override fun instantiate(name: String, value: java.lang.Class<*>): Feature {
      return Feature.Class(name, value)
    }
  }

  object Boolean : FeatureValueType<kotlin.Boolean>() {
    override fun instantiate(name: String, value: kotlin.Boolean): Feature {
      return Feature.Boolean(name, value)
    }
  }

  object Version : FeatureValueType<com.intellij.openapi.util.Version>() {
    override fun instantiate(name: String, value: com.intellij.openapi.util.Version): Feature {
      return Feature.Version(name, value)
    }
  }

  object Language : FeatureValueType<com.intellij.lang.Language>() {
    override fun instantiate(name: String, value: com.intellij.lang.Language): Feature {
      return Feature.Language(name, value)
    }
  }

  override fun toString(): String = this.javaClass.simpleName
}
