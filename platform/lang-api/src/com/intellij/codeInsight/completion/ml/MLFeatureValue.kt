// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml


sealed class MLFeatureValue {
  companion object {
    private val TRUE = BinaryValue(true)
    private val FALSE = BinaryValue(false)

    @JvmStatic
    fun binary(value: Boolean): MLFeatureValue = if (value) TRUE else FALSE

    @JvmStatic
    fun float(value: Int): MLFeatureValue = FloatValue(value.toDouble())

    @JvmStatic
    fun float(value: Double): MLFeatureValue = FloatValue(value)

    // alias for float(Int), but could be used from java sources (since java forbids to use method named like a keyword)
    @JvmStatic
    fun numerical(value: Int): MLFeatureValue = float(value)

    // alias for float(Double), but could be used from java sources (since java forbids to use method named like a keyword)
    @JvmStatic
    fun numerical(value: Double): MLFeatureValue = float(value)

    @JvmStatic
    fun <T : Enum<*>> categorical(value: T): MLFeatureValue = CategoricalValue(value.toString())

    @JvmStatic
    fun <T : Class<*>> className(value: T, useSimpleName: Boolean = true): MLFeatureValue = ClassNameValue(value, useSimpleName)
  }

  abstract val value: Any

  data class BinaryValue internal constructor(override val value: Boolean) : MLFeatureValue()
  data class FloatValue internal constructor(override val value: Double) : MLFeatureValue()
  data class CategoricalValue internal constructor(override val value: String) : MLFeatureValue()
  data class ClassNameValue internal constructor(override val value: Class<*>, val useSimpleName: Boolean) : MLFeatureValue()
}
