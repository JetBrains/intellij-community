// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml


sealed class MLFeatureValue {
  companion object {
    @JvmStatic
    fun binary(value: Boolean): MLFeatureValue = if (value) BinaryValue.TRUE else BinaryValue.FALSE

    @JvmStatic
    fun float(value: Int): MLFeatureValue = FloatValue(value.toDouble())

    @JvmStatic
    fun float(value: Double): MLFeatureValue = FloatValue(value)

    // alias for float(Int), but could be used from java sources (since java forbids to use method named like a keyword)
    @JvmStatic
    fun numerical(value: Int): MLFeatureValue = float(value)

    // alias for float(Double), but could be used from java sources (since java forbids to use method named like a keyword)
    @JvmStatic
    fun numerical(value:Double): MLFeatureValue = float(value)

    @JvmStatic
    fun <T : Enum<*>> categorical(value: T): MLFeatureValue = CategoricalValue(value.toString())
  }

  protected abstract val value: Any
  fun asBinary(): Boolean? = value as? Boolean
  fun asFloat(): Double? = value as? Double
  fun asCategorical(): String? = value as? String

  private class BinaryValue private constructor(override val value: Boolean) : MLFeatureValue() {
    companion object {
      val TRUE = BinaryValue(true)
      val FALSE = BinaryValue(false)
    }

    override fun toString(): String {
      return if (value) "1" else "0"
    }
  }

  private class FloatValue(override val value: Double) : MLFeatureValue() {
    override fun toString(): String = value.toString()
  }

  private class CategoricalValue(override val value: String) : MLFeatureValue() {
    override fun toString(): String = value
  }
}
