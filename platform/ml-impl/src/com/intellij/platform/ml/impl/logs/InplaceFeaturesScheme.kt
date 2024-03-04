// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.*
import com.intellij.platform.ml.impl.session.*
import org.jetbrains.annotations.ApiStatus

/**
 * The currently used FUS logging scheme.
 * Inplace means that the features are logged beside the tiers' instances and
 * are not compressed in any way.
 *
 * An example of a FUS record could be found in the test resources:
 * [testResources/ml_logs.js](community/platform/ml-impl/testResources/ml_logs.js)
 */
@ApiStatus.Internal
class InplaceFeaturesScheme<P : Any, F> internal constructor(
  private val predictionField: EventField<F>,
  private val predictionTransformer: (P?) -> F?,
  private val approachDeclaration: MLTaskApproach.SessionDeclaration
) : EventSessionEventBuilder<P> {
  class FusScheme<P : Any, F>(
    private val predictionField: EventField<F>,
    private val predictionTransformer: (P?) -> F?,
  ) : EventSessionEventBuilder.EventScheme<P> {
    override fun createEventBuilder(approachDeclaration: MLTaskApproach.SessionDeclaration): EventSessionEventBuilder<P> = InplaceFeaturesScheme(
      predictionField,
      predictionTransformer,
      approachDeclaration
    )

    companion object {
      val DOUBLE: FusScheme<Double, Double> = FusScheme(DoubleEventField("prediction")) { it }
    }
  }

  override fun buildSessionFields(): SessionFields<P> {
    require(approachDeclaration.levelsScheme.isNotEmpty())
    return if (approachDeclaration.levelsScheme.size == 1)
      PredictionSessionFields(approachDeclaration.levelsScheme.first(), predictionField, predictionTransformer,
                              approachDeclaration.sessionFeatures)
    else
      NestableSessionFields(approachDeclaration.levelsScheme.first(), approachDeclaration.levelsScheme.drop(1),
                            predictionField, predictionTransformer, approachDeclaration.sessionFeatures)
  }

  override fun buildRecord(sessionStructure: AnalysedRootContainer<P>, sessionFields: SessionFields<P>): Array<EventPair<*>> {
    return sessionFields.buildEventPairs(sessionStructure).toTypedArray()
  }
}

private data class StringField(override val name: String, private val possibleValues: Set<String>) : PrimitiveEventField<String>() {
  override fun addData(fuData: FeatureUsageData, value: String) {
    fuData.addData(name, value)
  }

  override val validationRule = listOf(
    "{enum:${possibleValues.joinToString("|")}}"
  )
}

private data class VersionField(override val name: String) : PrimitiveEventField<String?>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#version}")

  override fun addData(fuData: FeatureUsageData, value: String?) {
    fuData.addVersionByString(value)
  }
}

private fun FeatureDeclaration<*>.toEventField(): EventField<*> {
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
  }
}

private fun <T : Enum<*>> Feature.Enum<T>.toEventPair(): EventPair<*> {
  return EnumEventField(declaration.name, valueType.enumClass, Enum<*>::name) with value
}

private fun <T> Feature.Nullable<T>.toEventPair(): EventPair<*>? {
  return value?.let {
    baseType.instantiate(this.declaration.name, it).toEventPair()
  }
}

private fun Feature.toEventPair(): EventPair<*>? {
  return when (this) {
    is Feature.TypedFeature<*> -> typedToEventPair()
  }
}

private fun <T> Feature.TypedFeature<T>.typedToEventPair(): EventPair<*>? {
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
    is Feature.Version -> VersionField(declaration.name) with this.value.toString()
  }
}

private class FeatureSet(featuresDeclarations: Set<FeatureDeclaration<*>>) : ObjectDescription() {
  init {
    for (featureDeclaration in featuresDeclarations) {
      field(featureDeclaration.toEventField())
    }
  }

  fun toObjectEventData(features: Set<Feature>) = ObjectEventData(features.mapNotNull { it.toEventPair() })
}

private fun Set<Feature>.toObjectEventData() = FeatureSet(this.map { it.declaration }.toSet()).toObjectEventData(this)

private data class TierDescriptionFields(
  val used: FeatureSet,
  val notUsed: FeatureSet,
) : ObjectDescription() {
  private val fieldUsed = ObjectEventField("used", used)
  private val fieldNotUsed = ObjectEventField("not_used", notUsed)
  private val fieldAmountUsedNonDeclaredFeatures = IntEventField("n_used_non_declared")
  private val fieldAmountNotUsedNonDeclaredFeatures = IntEventField("n_not_used_non_declared")

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

private data class AdditionalTierFields(val description: TierDescriptionFields) : ObjectDescription() {
  private val fieldInstanceId = IntEventField("id")
  private val fieldDescription = ObjectEventField("description", description)

  constructor(descriptionFeatures: Set<FeatureDeclaration<*>>)
    : this(TierDescriptionFields(used = FeatureSet(descriptionFeatures),
                                 notUsed = FeatureSet(descriptionFeatures)))

  init {
    field(fieldInstanceId)
    field(fieldDescription)
  }

  fun buildObjectEventData(tierInstance: TierInstance<*>,
                           descriptionPartition: DescriptionPartition) = ObjectEventData(
    fieldInstanceId with tierInstance.instance.hashCode(),
    fieldDescription with this.description.buildObjectEventData(descriptionPartition),
  )
}

private data class MainTierFields(
  val description: TierDescriptionFields,
  val analysis: FeatureSet,
) : ObjectDescription() {
  private val fieldInstanceId = IntEventField("id")
  private val fieldDescription = ObjectEventField("description", description)
  private val fieldAnalysis = ObjectEventField("analysis", analysis)

  constructor(descriptionFeatures: Set<FeatureDeclaration<*>>, analysisFeatures: Set<FeatureDeclaration<*>>)
    : this(TierDescriptionFields(used = FeatureSet(descriptionFeatures),
                                 notUsed = FeatureSet(descriptionFeatures)),
           FeatureSet(analysisFeatures))

  init {
    field(fieldInstanceId)
    field(fieldDescription)
    field(fieldAnalysis)
  }

  fun buildObjectEventData(tierInstance: TierInstance<*>,
                           descriptionPartition: DescriptionPartition,
                           analysis: Set<Feature>) = ObjectEventData(
    fieldInstanceId with tierInstance.instance.hashCode(),
    fieldDescription with this.description.buildObjectEventData(descriptionPartition),
    fieldAnalysis with this.analysis.toObjectEventData(analysis)
  )
}

private data class SessionAnalysisFields<P : Any>(
  val featuresPerKey: Map<String, Set<FeatureDeclaration<*>>>
) : SessionFields<P>() {
  val fieldsPerKey: Map<String, ObjectEventField> = featuresPerKey.entries.associate { (key, keyFeatures) ->
    key to ObjectEventField(key, FeatureSet(keyFeatures))
  }

  init {
    fieldsPerKey.values.forEach { field(it) }
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    require(sessionStructure is SessionTree.RootContainer<SessionAnalysis, AnalysedTierData, P>)
    return sessionStructure.rootData.entries.map { (key, keyFeatures) ->
      val keyFeaturesDeclaration = requireNotNull(featuresPerKey[key]) {
        "Key $key was not declared as session features key, declared keys: ${featuresPerKey.keys}"
      }
      val objectEventField = fieldsPerKey.getValue(key)
      val keyFeatureSet = FeatureSet(keyFeaturesDeclaration)
      objectEventField with keyFeatureSet.toObjectEventData(keyFeatures)
    }
  }
}

private class MainTierSet<P : Any>(mainTierScheme: PerTier<MainTierScheme>) : SessionFields<P>() {
  val tiersDeclarations: PerTier<MainTierFields> = mainTierScheme.entries.associate { (tier, tierScheme) ->
    tier to MainTierFields(tierScheme.description, tierScheme.analysis)
  }
  val fieldPerTier: PerTier<ObjectEventField> = tiersDeclarations.entries.associate { (tier, tierFields) ->
    tier to ObjectEventField(tier.name, tierFields)
  }

  init {
    fieldPerTier.values.forEach { field(it) }
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    val level = sessionStructure.levelData.mainInstances
    return level.entries.map { (tierInstance, data) ->
      val tierField = requireNotNull(fieldPerTier[tierInstance.tier]) {
        "Tier ${tierInstance.tier} is now allowed here: only ${fieldPerTier.keys} are registered"
      }
      val tierDeclaration = tiersDeclarations.getValue(tierInstance.tier)
      tierField with tierDeclaration.buildObjectEventData(tierInstance, data.description, data.analysis)
    }
  }
}

private class AdditionalTierSet<P : Any>(additionalTierScheme: PerTier<AdditionalTierScheme>) : SessionFields<P>() {
  val tiersDeclarations: PerTier<AdditionalTierFields> = additionalTierScheme.entries.associate { (tier, tierScheme) ->
    tier to AdditionalTierFields(tierScheme.description)
  }
  val fieldPerTier: PerTier<ObjectEventField> = tiersDeclarations.entries.associate { (tier, tierFields) ->
    tier to ObjectEventField(tier.name, tierFields)
  }

  init {
    fieldPerTier.values.forEach { field(it) }
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    val level = sessionStructure.levelData.additionalInstances
    return level.entries.map { (tierInstance, data) ->
      val tierField = requireNotNull(fieldPerTier[tierInstance.tier]) {
        "Tier ${tierInstance.tier} is now allowed here: only ${fieldPerTier.keys} are registered"
      }
      val tierDeclaration = tiersDeclarations.getValue(tierInstance.tier)
      tierField with tierDeclaration.buildObjectEventData(tierInstance, data.description)
    }
  }
}

private data class PredictionSessionFields<P : Any, F>(
  val declarationMainTierSet: MainTierSet<P>,
  val declarationAdditionalTierSet: AdditionalTierSet<P>,
  val fieldPrediction: EventField<F>,
  val serializePrediction: (P?) -> F?,
  val sessionAnalysisFields: SessionAnalysisFields<P>?
) : SessionFields<P>() {
  private val fieldMainInstances = ObjectEventField("main", declarationMainTierSet)
  private val fieldAdditionalInstances = ObjectEventField("additional", declarationAdditionalTierSet)
  private val fieldSessionAnalysis = sessionAnalysisFields?.let { ObjectEventField("session", sessionAnalysisFields) }

  constructor(levelScheme: LevelScheme,
              fieldPrediction: EventField<F>,
              serializePrediction: (P?) -> F?,
              sessionAnalysisFields: Map<String, Set<FeatureDeclaration<*>>>?)
    : this(MainTierSet(levelScheme.main),
           AdditionalTierSet(levelScheme.additional),
           fieldPrediction,
           serializePrediction,
           sessionAnalysisFields?.let { SessionAnalysisFields(it) })

  init {
    field(fieldMainInstances)
    field(fieldAdditionalInstances)
    field(fieldPrediction)
    fieldSessionAnalysis?.let { field(it) }
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    require(sessionStructure is SessionTree.PredictionContainer<*, *, P>)
    val eventPairs = mutableListOf<EventPair<*>>(
      fieldMainInstances with declarationMainTierSet.buildObjectEventData(sessionStructure),
      fieldAdditionalInstances with declarationAdditionalTierSet.buildObjectEventData(sessionStructure),
    )
    fieldSessionAnalysis?.let {
      eventPairs += it with sessionAnalysisFields!!.buildObjectEventData(sessionStructure)
    }

    val serializedPrediction = serializePrediction(sessionStructure.prediction)
    serializedPrediction?.let {
      eventPairs += fieldPrediction with it
    }

    return eventPairs
  }
}

private data class NestableSessionFields<P : Any, F>(
  val declarationMainTierSet: MainTierSet<P>,
  val declarationAdditionalTierSet: AdditionalTierSet<P>,
  val declarationNestedSession: SessionFields<P>,
  val sessionAnalysisFields: SessionAnalysisFields<P>?
) : SessionFields<P>() {
  private val fieldMainInstances = ObjectEventField("main", declarationMainTierSet)
  private val fieldAdditionalInstances = ObjectEventField("additional", declarationAdditionalTierSet)
  private val fieldNestedSessions = ObjectListEventField("nested", declarationNestedSession)
  private val fieldSessionAnalysis = sessionAnalysisFields?.let { ObjectEventField("session", sessionAnalysisFields) }

  constructor(levelScheme: LevelScheme,
              deeperLevelsSchemes: List<LevelScheme>,
              predictionField: EventField<F>,
              serializePrediction: (P?) -> F?,
              sessionAnalysisFields: Map<String, Set<FeatureDeclaration<*>>>?)
    : this(MainTierSet(levelScheme.main),
           AdditionalTierSet(levelScheme.additional),
           if (deeperLevelsSchemes.size == 1)
             PredictionSessionFields(deeperLevelsSchemes.first(), predictionField, serializePrediction, null)
           else {
             require(deeperLevelsSchemes.size > 1)
             NestableSessionFields(deeperLevelsSchemes.first(), deeperLevelsSchemes.drop(1),
                                   predictionField, serializePrediction, null)
           },
           sessionAnalysisFields?.let { SessionAnalysisFields(it) }
  )

  init {
    field(fieldMainInstances)
    field(fieldAdditionalInstances)
    field(fieldNestedSessions)
    fieldSessionAnalysis?.let { field(it) }
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    require(sessionStructure is SessionTree.ChildrenContainer)
    val children = sessionStructure.children
    val eventPairs = mutableListOf<EventPair<*>>(
      fieldMainInstances with declarationMainTierSet.buildObjectEventData(sessionStructure),
      fieldAdditionalInstances with declarationAdditionalTierSet.buildObjectEventData(sessionStructure),
      fieldNestedSessions with children.map { nestedSession -> declarationNestedSession.buildObjectEventData(nestedSession) }
    )

    fieldSessionAnalysis?.let {
      eventPairs += fieldSessionAnalysis with sessionAnalysisFields!!.buildObjectEventData(sessionStructure)
    }

    return eventPairs
  }
}
