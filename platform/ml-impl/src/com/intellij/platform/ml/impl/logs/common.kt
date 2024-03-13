// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.FeatureValueType
import com.intellij.platform.ml.impl.session.DescriptionPartition


internal data class StringField(override val name: String, private val possibleValues: Set<String>) : PrimitiveEventField<String>() {
  override fun addData(fuData: FeatureUsageData, value: String) {
    fuData.addData(name, value)
  }

  override val validationRule = listOf(
    "{enum:${possibleValues.joinToString("|")}}"
  )
}

internal data class VersionField(override val name: String) : PrimitiveEventField<Version?>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#version}")

  override fun addData(fuData: FeatureUsageData, value: Version?) {
    fuData.addVersion(value)
  }
}

internal fun FeatureDeclaration<*>.toEventField(): EventField<*> {
  return when (val valueType = type) {
    is FeatureValueType.Enum<*> -> EnumEventField(name, valueType.enumClass, Enum<*>::name)
    is FeatureValueType.Int -> IntEventField(name)
    is FeatureValueType.Long -> LongEventField(name)
    is FeatureValueType.Class -> ClassEventField(name)
    is FeatureValueType.Boolean -> BooleanEventField(name)
    is FeatureValueType.Double -> DoubleEventField(name)
    is FeatureValueType.Float -> FloatEventField(name)
    is FeatureValueType.Nullable -> FeatureDeclaration(name, valueType.baseType).toEventField()
    is FeatureValueType.Categorical -> StringField(name, valueType.possibleValues)
    is FeatureValueType.Version -> VersionField(name)
    FeatureValueType.Language -> EventFields.Language(name)
  }
}

internal fun <T : Enum<*>> Feature.Enum<T>.toEventPair(): EventPair<*> {
  return EnumEventField(declaration.name, valueType.enumClass, Enum<*>::name) with value
}

internal fun <T> Feature.Nullable<T>.toEventPair(): EventPair<*>? {
  return value?.let {
    baseType.instantiate(this.declaration.name, it).toEventPair()
  }
}

internal fun Feature.toEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.TypedFeature<*> -> typedToEventPair()
  }
}

internal fun <T> Feature.TypedFeature<T>.typedToEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.Boolean -> BooleanEventField(declaration.name) with this.value
    is Feature.Categorical -> StringField(declaration.name, this.valueType.possibleValues) with this.value
    is Feature.Class -> ClassEventField(declaration.name) with this.value
    is Feature.Double -> DoubleEventField(declaration.name) with this.value
    is Feature.Enum<*> -> toEventPair()
    is Feature.Float -> FloatEventField(declaration.name) with this.value
    is Feature.Int -> IntEventField(declaration.name) with this.value
    is Feature.Long -> LongEventField(declaration.name) with this.value
    is Feature.Nullable<*> -> toEventPair()
    is Feature.Version -> VersionField(declaration.name) with this.value
    is Feature.Language ->  EventFields.Language(name) with this.value
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
  private val fieldUsed = ObjectEventField("used", used)
  private val fieldNotUsed = ObjectEventField("not_used", notUsed)
  private val fieldAmountUsedNonDeclaredFeatures = IntEventField("n_used_non_declared")
  private val fieldAmountNotUsedNonDeclaredFeatures = IntEventField("n_not_used_non_declared")

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
