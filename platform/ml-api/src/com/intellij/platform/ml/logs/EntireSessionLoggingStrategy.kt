// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.platform.ml.MLApiPlatform
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.TierInstance
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.logs.schema.*
import com.intellij.platform.ml.session.*
import org.jetbrains.annotations.ApiStatus

/**
 * The logging scheme that is logging entire session into one event.
 *
 * If your sessions of your task are large enough, then it's possible that they
 * won't fit into one event, as it has a limit.
 * In such a case try using [SessionAsMultipleEventsLoggingStrategy].
 */
@ApiStatus.Internal
class EntireSessionLoggingStrategy<P : Any, F>(
  private val predictionField: EventField<F>,
  private val predictionTransformer: (P?) -> F?,
) : MLSessionLoggingStrategy<P> {
  override fun registerLogComponents(sessionAnalysisDeclaration: List<EventField<*>>,
                                     sessionStructureAnalysisDeclaration: List<AnalysedLevelScheme>,
                                     componentPrefix: String,
                                     componentRegister: MLSessionComponentRegister): MLSessionLogger<P> {

    require(sessionStructureAnalysisDeclaration.isNotEmpty())
    val sessionStructureFields = if (sessionStructureAnalysisDeclaration.size == 1)
      PredictionSessionFields(sessionStructureAnalysisDeclaration.first(), predictionField, predictionTransformer)
    else
      NestableSessionFields(sessionStructureAnalysisDeclaration.first(), sessionStructureAnalysisDeclaration.drop(1), predictionField,
                            predictionTransformer)

    val fieldSessionStructure = ObjectEventField("structure", null, sessionStructureFields)
    val fieldSession = ObjectEventField("session", null, ObjectDescription(sessionAnalysisDeclaration))

    val fusLogger = componentRegister.registerComponent(componentPrefix, listOf(fieldSession, fieldSessionStructure))

    return object : MLSessionLogger<P> {
      override fun logComponents(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, permanentCallParameters: Environment, session: List<EventPair<*>>, structure: AnalysedRootContainer<P>?) {
        fusLogger.log(buildList {
          if (structure != null) add(fieldSessionStructure with sessionStructureFields.buildObjectEventData(structure))
          add(fieldSession with ObjectEventData(session))
        }
        )
      }
    }
  }

  companion object {
    val DOUBLE: MLSessionLoggingStrategy<Double> = EntireSessionLoggingStrategy(DoubleEventField("prediction", null)) { it }
    val UNIT: MLSessionLoggingStrategy<Unit> = EntireSessionLoggingStrategy(BooleanEventField("prediction", null)) { null }
  }
}

private abstract class SessionFields<P : Any> : ObjectDescription() {
  fun buildObjectEventData(sessionStructure: AnalysedSessionTree<P>) = ObjectEventData(buildEventPairs(sessionStructure))

  abstract fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>>
}

private data class AdditionalTierFields(val description: TierDescriptionFields) : ObjectDescription() {
  private val fieldInstanceId = IntEventField("id", null)
  private val fieldDescription = ObjectEventField("description", null, description)

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
  private val fieldInstanceId = IntEventField("id", null)
  private val fieldDescription = ObjectEventField("description", null, description)
  private val fieldAnalysis = ObjectEventField("analysis", null, analysis)

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

private class MainTierSet<P : Any>(mainTierScheme: PerTier<AnalysedTierScheme>) : SessionFields<P>() {
  val tiersDeclarations: PerTier<MainTierFields> = mainTierScheme.entries.associate { (tier, tierScheme) ->
    tier to MainTierFields(tierScheme.description, tierScheme.analysis)
  }
  val fieldPerTier: PerTier<ObjectEventField> = tiersDeclarations.entries.associate { (tier, tierFields) ->
    tier to ObjectEventField(tier.name, null, tierFields)
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

private class AdditionalTierSet<P : Any>(additionalTierScheme: PerTier<DescribedTierScheme>) : SessionFields<P>() {
  val tiersDeclarations: PerTier<AdditionalTierFields> = additionalTierScheme.entries.associate { (tier, tierScheme) ->
    tier to AdditionalTierFields(tierScheme.description)
  }
  val fieldPerTier: PerTier<ObjectEventField> = tiersDeclarations.entries.associate { (tier, tierFields) ->
    tier to ObjectEventField(tier.name, null, tierFields)
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
) : SessionFields<P>() {
  private val fieldMainInstances = ObjectEventField("main", null, declarationMainTierSet)
  private val fieldAdditionalInstances = ObjectEventField("additional", null, declarationAdditionalTierSet)

  constructor(levelScheme: AnalysedLevelScheme,
              fieldPrediction: EventField<F>,
              serializePrediction: (P?) -> F?)
    : this(MainTierSet(levelScheme.main),
           AdditionalTierSet(levelScheme.additional),
           fieldPrediction,
           serializePrediction)

  init {
    field(fieldMainInstances)
    field(fieldAdditionalInstances)
    field(fieldPrediction)
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    require(sessionStructure is SessionTree.PredictionContainer<*, *, P>)
    val eventPairs = mutableListOf<EventPair<*>>(
      fieldMainInstances with declarationMainTierSet.buildObjectEventData(sessionStructure),
      fieldAdditionalInstances with declarationAdditionalTierSet.buildObjectEventData(sessionStructure),
    )

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
) : SessionFields<P>() {
  private val fieldMainInstances = ObjectEventField("main", null, declarationMainTierSet)
  private val fieldAdditionalInstances = ObjectEventField("additional", null, declarationAdditionalTierSet)
  private val fieldNestedSessions = ObjectListEventField("nested", null, declarationNestedSession)

  constructor(levelScheme: AnalysedLevelScheme,
              deeperLevelsSchemes: List<AnalysedLevelScheme>,
              predictionField: EventField<F>,
              serializePrediction: (P?) -> F?)
    : this(MainTierSet(levelScheme.main),
           AdditionalTierSet(levelScheme.additional),
           if (deeperLevelsSchemes.size == 1)
             PredictionSessionFields(deeperLevelsSchemes.first(), predictionField, serializePrediction)
           else {
             require(deeperLevelsSchemes.size > 1)
             NestableSessionFields(deeperLevelsSchemes.first(), deeperLevelsSchemes.drop(1),
                                   predictionField, serializePrediction)
           }
  )

  init {
    field(fieldMainInstances)
    field(fieldAdditionalInstances)
    field(fieldNestedSessions)
    field(fieldNestedSessions)
  }

  override fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>> {
    require(sessionStructure is SessionTree.ChildrenContainer)
    val children = sessionStructure.children
    val eventPairs = mutableListOf<EventPair<*>>(
      fieldMainInstances with declarationMainTierSet.buildObjectEventData(sessionStructure),
      fieldAdditionalInstances with declarationAdditionalTierSet.buildObjectEventData(sessionStructure),
      fieldNestedSessions with children.map { nestedSession -> declarationNestedSession.buildObjectEventData(nestedSession) }
    )

    return eventPairs
  }
}
