// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureValueType
import com.intellij.platform.ml.logs.schema.*
import com.intellij.platform.ml.session.DescriptionPartition

internal fun FeatureDeclaration<*>.toEventField(): EventField<*> {
  return when (val valueType = type) {
    is FeatureValueType.Enum<*> -> EnumEventField(name, null, valueType.enumClass, Enum<*>::name)
    is FeatureValueType.Int -> IntEventField(name, null)
    is FeatureValueType.Long -> LongEventField(name, null)
    is FeatureValueType.Class -> ClassEventField(name, null)
    is FeatureValueType.Boolean -> BooleanEventField(name, null)
    is FeatureValueType.Double -> DoubleEventField(name, null)
    is FeatureValueType.Float -> FloatEventField(name, null)
    is FeatureValueType.Nullable -> FeatureDeclaration(name, valueType.baseType).toEventField()
    is FeatureValueType.Categorical -> StringEventField(name, null, valueType.possibleValues.toList())
    is FeatureValueType.Custom<*> -> valueType.eventFieldBuilder(name)
  }
}

internal fun <T : Enum<*>> Feature.Enum<T>.toEventPair(): EventPair<*> {
  return EnumEventField(declaration.name, null, valueType.enumClass, Enum<*>::name) with value
}

internal fun <T> Feature.Nullable<T>.toEventPair(): EventPair<*>? {
  return value?.let {
    baseType.instantiate(this.declaration.name, it).toEventPair()
  }
}

internal fun Feature.toEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.TypedFeature<*> -> typedToEventPair()
    is Feature.Custom<*> -> eventPair
  }
}

internal fun <T> Feature.TypedFeature<T>.typedToEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.Boolean -> BooleanEventField(declaration.name, null) with this.value
    is Feature.Categorical -> StringEventField(declaration.name, null, this.valueType.possibleValues.toList()) with this.value
    is Feature.Class -> ClassEventField(declaration.name, null) with this.value
    is Feature.Double -> DoubleEventField(declaration.name, null) with this.value
    is Feature.Enum<*> -> toEventPair()
    is Feature.Float -> FloatEventField(declaration.name, null) with this.value
    is Feature.Int -> IntEventField(declaration.name, null) with this.value
    is Feature.Long -> LongEventField(declaration.name, null) with this.value
    is Feature.Nullable<*> -> toEventPair()
  }
}

internal class FeatureSet(featureDeclarations: Set<FeatureDeclaration<*>>) : ObjectDescription() {
  init {
    for (featureDeclaration in featureDeclarations) {
      field(featureDeclaration.toEventField())
    }
  }

  fun toObjectEventData(features: Set<Feature>) = ObjectEventData(features.mapNotNull { it.toEventPair() })
}

internal fun Set<Feature>.toObjectEventData() = FeatureSet(this.map { it.declaration }.toSet()).toObjectEventData(this)

internal data class TierDescriptionFields(
  val used: FeatureSet,
  val notUsed: FeatureSet,
) : ObjectDescription() {
  private val fieldUsed = ObjectEventField("used", null, used)
  private val fieldNotUsed = ObjectEventField("not_used", null, notUsed)
  private val fieldAmountUsedNonDeclaredFeatures = IntEventField("n_used_non_declared", null)
  private val fieldAmountNotUsedNonDeclaredFeatures = IntEventField("n_not_used_non_declared", null)

  constructor(descriptionFeatures: Set<FeatureDeclaration<*>>)
    : this(used = FeatureSet(descriptionFeatures), notUsed = FeatureSet(descriptionFeatures))

  init {
    field(fieldUsed)
    field(fieldNotUsed)
    field(fieldAmountUsedNonDeclaredFeatures)
    field(fieldAmountNotUsedNonDeclaredFeatures)
  }

  fun buildEventPairs(descriptionPartition: DescriptionPartition): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>(
      fieldUsed with descriptionPartition.declared.used.toObjectEventData(),
      fieldNotUsed with descriptionPartition.declared.notUsed.toObjectEventData(),
    )
    descriptionPartition.nonDeclared.used.let {
      if (it.isNotEmpty()) result += fieldAmountUsedNonDeclaredFeatures with it.size
    }
    descriptionPartition.nonDeclared.notUsed.let {
      if (it.isNotEmpty()) result += fieldAmountUsedNonDeclaredFeatures with it.size
    }
    return result
  }

  fun buildObjectEventData(descriptionPartition: DescriptionPartition) = ObjectEventData(
    buildEventPairs(descriptionPartition)
  )
}
