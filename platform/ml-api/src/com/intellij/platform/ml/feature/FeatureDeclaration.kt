// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.feature

import org.jetbrains.annotations.ApiStatus


/**
 * Represents declaration of a Tier's feature.
 *
 * All tiers that end up in the ML Model's inference must be declared statically
 * (i.e., via Extension Points), as FUS logs' validator must be aware of every
 * feature that will be logged.
 *
 * @param name The feature's name that is unique for this tier. It may not contain special symbols.
 * @param type The feature's type.
 * @param T The type of the value, with which the [type] can be instantiated ([FeatureValueType.instantiate]).
 */
@ApiStatus.Internal
class FeatureDeclaration<T>(
  val name: String,
  val type: FeatureValueType<T>
) {
  init {
    require(name.all { it.isLetterOrDigit() || it == '_' }) {
      "Invalid feature name '$name': it shall not contain special symbols"
    }
  }

  override fun toString(): String {
    return "{$name: $type}"
  }



  /**
   * Shortcut for the feature's instantiation
   */
  infix fun with(value: T): Feature {
    return type.instantiate(name, value)
  }

  /**
   * Signifies that the feature can be instantiated with null values.
   */
  fun nullable(): FeatureDeclaration<T?> {
    require(type !is FeatureValueType.Nullable<*>) { "Repeated declaration as 'nullable'" }
    return FeatureDeclaration(name, FeatureValueType.Nullable(type))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FeatureDeclaration<*>

    if (name != other.name) return false
    if (type != other.type) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }

  companion object {
    inline fun <reified T : Enum<*>> enum(name: String) = FeatureDeclaration(name, FeatureValueType.Enum(T::class.java))

    fun int(name: String) = FeatureDeclaration(name, FeatureValueType.Int)

    fun double(name: String) = FeatureDeclaration(name, FeatureValueType.Double)

    fun float(name: String) = FeatureDeclaration(name, FeatureValueType.Float)

    fun long(name: String) = FeatureDeclaration(name, FeatureValueType.Long)

    fun aClass(name: String) = FeatureDeclaration(name, FeatureValueType.Class)

    fun boolean(name: String) = FeatureDeclaration(name, FeatureValueType.Boolean)

    fun categorical(name: String, possibleValues: Set<String>) = FeatureDeclaration(name, FeatureValueType.Categorical(possibleValues))
  }
}
