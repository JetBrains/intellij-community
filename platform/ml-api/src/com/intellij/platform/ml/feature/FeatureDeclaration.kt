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
 * @param descriptionProvider long feature description; since the description is not required during the typical application usage, the object allocation is deferred
 */
@ApiStatus.Internal
class FeatureDeclaration<T>(
  val name: String,
  val type: FeatureValueType<T>,
  val descriptionProvider: () -> String
) {

  @Deprecated("Use primary constructor")
  constructor(name: String, type: FeatureValueType<T>): this(name, type, { "" })

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
    return type.instantiate(name, value, descriptionProvider)
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
    @Deprecated("Use the declaration with description")
    inline fun <reified T : Enum<*>> enum(name: String) = FeatureDeclaration(name, FeatureValueType.Enum(T::class.java))
    inline fun <reified T : Enum<*>> enum(name: String, noinline descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Enum(T::class.java), descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with description")
    fun int(name: String) = FeatureDeclaration(name, FeatureValueType.Int)
    fun int(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Int, descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with description")
    fun double(name: String) = FeatureDeclaration(name, FeatureValueType.Double)
    fun double(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Double, descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with description")
    fun float(name: String) = FeatureDeclaration(name, FeatureValueType.Float)
    fun float(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Float, descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with description")
    fun long(name: String) = FeatureDeclaration(name, FeatureValueType.Long)
    fun long(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Long, descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with description")
    fun aClass(name: String) = FeatureDeclaration(name, FeatureValueType.Class)
    fun aClass(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Class, descriptionProvider)

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use the declaration with description")
    fun boolean(name: String) = FeatureDeclaration(name, FeatureValueType.Boolean)
    fun boolean(name: String, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Boolean, descriptionProvider)

    @Deprecated("Use the declaration with description")
    fun categorical(name: String, possibleValues: Set<String>) = FeatureDeclaration(name, FeatureValueType.Categorical(possibleValues))
    fun categorical(name: String, possibleValues: Set<String>, descriptionProvider: () -> String) = FeatureDeclaration(name, FeatureValueType.Categorical(possibleValues), descriptionProvider)
  }
}
