// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.feature

import com.intellij.platform.ml.logs.schema.EventPair
import org.jetbrains.annotations.ApiStatus

/**
 * An instantiated tier's feature. It could be either
 * description's feature (that was provided by a [com.intellij.platform.ml.TierDescriptor]), or an
 * analysis feature (that was provided by a [com.intellij.platform.ml.analysis.StructureAnalyser]).
 *
 * If you need another type of feature, and it is not supported yet, contact the ML API developers.
 */
@ApiStatus.Internal
sealed class Feature {
  /**
   * A statically defined declaration of this feature that includes all the information, except the value.
   */
  abstract val declaration: FeatureDeclaration<*>

  /**
   * A computed nullable value
   */
  abstract val value: Any?

  override fun equals(other: Any?): kotlin.Boolean {
    if (other !is Feature) return false
    return other.declaration == this.declaration && other.value == this.value
  }

  sealed class TypedFeature<T>(
    val name: String,
    override val value: T,
    private val descriptionProvider: () -> String
  ) : Feature() {

    @Deprecated("Use the primary constructor instead")
    constructor(name: String, value: T) : this(name, value, { "" })

    abstract val valueType: FeatureValueType<T>

    override val declaration: FeatureDeclaration<*>
      get() = FeatureDeclaration(name, valueType, descriptionProvider)
  }

  override fun hashCode(): kotlin.Int {
    return this.declaration.hashCode().xor(this.value.hashCode())
  }

  override fun toString(): String {
    return "Feature{declaration=$declaration, value=$value}"
  }

  class Enum<T : kotlin.Enum<*>>(name: String, value: T, descriptionProvider: () -> String) : TypedFeature<T>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Enum(value.javaClass)
  }

  class Int(name: String, value: kotlin.Int, descriptionProvider: () -> String) : TypedFeature<kotlin.Int>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Int
  }

  class Boolean(name: String, value: kotlin.Boolean, descriptionProvider: () -> String) : TypedFeature<kotlin.Boolean>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Boolean
  }

  class Float(name: String, value: kotlin.Float, descriptionProvider: () -> String) : TypedFeature<kotlin.Float>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Float
  }

  class Double(name: String, value: kotlin.Double, descriptionProvider: () -> String) : TypedFeature<kotlin.Double>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Double
  }

  class Long(name: String, value: kotlin.Long, descriptionProvider: () -> String) : TypedFeature<kotlin.Long>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Long
  }

  class Class(name: String, value: java.lang.Class<*>, descriptionProvider: () -> String) : TypedFeature<java.lang.Class<*>>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Class
  }

  class Nullable<T>(name: String, value: T?, val baseType: FeatureValueType<T>, descriptionProvider: () -> String)
    : TypedFeature<T?>(name, value, descriptionProvider) {
    override val valueType = FeatureValueType.Nullable(baseType)
  }

  class Categorical(name: String, value: String, possibleValues: Set<String>)
    : TypedFeature<String>(name, value) {
    override val valueType = FeatureValueType.Categorical(possibleValues)
  }

  abstract class Custom<T>(val name: String, override val value: T, val descriptionProvider: () -> String) : Feature() {
    abstract val valueType: FeatureValueType.Custom<T>

    override val declaration: FeatureDeclaration<*>
      get() = FeatureDeclaration(name, valueType, descriptionProvider)

    val eventPair: EventPair<T>
      get() = valueType.eventFieldBuilder(name, descriptionProvider) with value
  }

  companion object {
    fun Feature.toCompactString(): String {
      return "${this.declaration.name}=${this.value}"
    }
  }
}
