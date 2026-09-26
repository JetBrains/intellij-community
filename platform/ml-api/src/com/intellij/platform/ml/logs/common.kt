// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.feature.FeatureValueType
import com.intellij.platform.ml.logs.schema.BooleanEventField
import com.intellij.platform.ml.logs.schema.ClassEventField
import com.intellij.platform.ml.logs.schema.DoubleEventField
import com.intellij.platform.ml.logs.schema.EnumEventField
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
import com.intellij.platform.ml.logs.schema.FloatEventField
import com.intellij.platform.ml.logs.schema.IntEventField
import com.intellij.platform.ml.logs.schema.LongEventField
import com.intellij.platform.ml.logs.schema.ObjectDescription
import com.intellij.platform.ml.logs.schema.ObjectEventData
import com.intellij.platform.ml.logs.schema.ObjectEventField
import com.intellij.platform.ml.logs.schema.StringEventField
import com.intellij.platform.ml.session.DescriptionPartition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("Provide a description")
val NO_DESCRIPTION: () -> String = { "" }

internal fun FeatureDeclaration<*>.toEventField(): EventField<*> {
  return when (val valueType = type) {
    is FeatureValueType.Enum<*> -> EnumEventField(name, descriptionProvider, valueType.enumClass, Enum<*>::name)
    is FeatureValueType.Int -> IntEventField(name, descriptionProvider)
    is FeatureValueType.Long -> LongEventField(name, descriptionProvider)
    is FeatureValueType.Class -> ClassEventField(name, descriptionProvider)
    is FeatureValueType.Boolean -> BooleanEventField(name, descriptionProvider)
    is FeatureValueType.Double -> DoubleEventField(name, descriptionProvider)
    is FeatureValueType.Float -> FloatEventField(name, descriptionProvider)
    is FeatureValueType.Nullable -> FeatureDeclaration(name, valueType.baseType, descriptionProvider).toEventField()
    is FeatureValueType.Categorical -> StringEventField(name, descriptionProvider, valueType.possibleValues.toList())
    is FeatureValueType.Custom<*> -> valueType.eventFieldBuilder(name, descriptionProvider)
  }
}

internal fun <T : Enum<*>> Feature.Enum<T>.toEventPair(): EventPair<*> {
  return EnumEventField(declaration.name, declaration.descriptionProvider, valueType.enumClass, Enum<*>::name) with value
}

internal fun <T> Feature.Nullable<T>.toEventPair(): EventPair<*>? {
  return value?.let {
    baseType.instantiate(this.declaration.name, it, this.declaration.descriptionProvider).toEventPair()
  }
}

internal fun Feature.toEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.TypedFeature<*> -> typedToEventPair()
    is Feature.Custom<*> -> eventPair
  }
}

/**
 * fixme: instead of the description value, pass on the description provider, when event field api is able to accept it
 */
internal fun <T> Feature.TypedFeature<T>.typedToEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.Boolean -> BooleanEventField(declaration.name, declaration.descriptionProvider) with this.value
    is Feature.Categorical -> StringEventField(declaration.name, declaration.descriptionProvider, this.valueType.possibleValues.toList()) with this.value
    is Feature.Class -> ClassEventField(declaration.name, declaration.descriptionProvider) with this.value
    is Feature.Double -> DoubleEventField(declaration.name, declaration.descriptionProvider) with this.value
    is Feature.Enum<*> -> toEventPair()
    is Feature.Float -> FloatEventField(declaration.name, declaration.descriptionProvider) with this.value
    is Feature.Int -> IntEventField(declaration.name, declaration.descriptionProvider) with this.value
    is Feature.Long -> LongEventField(declaration.name, declaration.descriptionProvider) with this.value
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
  private val fieldUsed = ObjectEventField("used", NO_DESCRIPTION, used)
  private val fieldNotUsed = ObjectEventField("not_used", NO_DESCRIPTION, notUsed)
  private val fieldAmountUsedNonDeclaredFeatures = IntEventField("n_used_non_declared", NO_DESCRIPTION)
  private val fieldAmountNotUsedNonDeclaredFeatures = IntEventField("n_not_used_non_declared", NO_DESCRIPTION)

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
