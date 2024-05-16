// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.platform.ml.MLApiPlatform
import com.intellij.platform.ml.Tier
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.logs.schema.*
import com.intellij.platform.ml.session.*
import org.jetbrains.annotations.ApiStatus


/**
 * The logging scheme that is logging splitting session into separate events
 *  to bypass the event's size limit.
 *
 *  If sessions in your ml task are small enough, you could try using [EntireSessionLoggingStrategy].
 */
@ApiStatus.Internal
class SessionAsMultipleEventsLoggingStrategy<P : Any, F>(
  private val fieldPrediction: EventField<F>,
  private val predictionTransformer: (P?) -> F?
) : MLSessionLoggingStrategy<P> {

  override fun registerLogComponents(
    sessionAnalysisDeclaration: List<EventField<*>>,
    sessionStructureAnalysisDeclaration: List<AnalysedLevelScheme>,
    componentPrefix: String,
    componentRegister: MLSessionComponentRegister
  ): MLSessionLogger<P> {
    require(sessionStructureAnalysisDeclaration.isNotEmpty())

    val sessionId = 0 // TODO

    val logStructure: (Int, AnalysedRootContainer<P>) -> Unit = if (sessionStructureAnalysisDeclaration.size == 1) {
      ComponentSolitaryLeaf.register(fieldPrediction, predictionTransformer, componentRegister, sessionStructureAnalysisDeclaration)
    }
    else {
      ComponentComplexRoot.register(fieldPrediction, predictionTransformer, componentRegister, sessionStructureAnalysisDeclaration)
    }
    val logSession = ComponentSomeAnalysis(COMPONENT_NAME_SESSION).register(componentRegister, sessionAnalysisDeclaration)

    return object : MLSessionLogger<P> {
      override fun logComponents(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, permanentCallParameters: Environment, session: List<EventPair<*>>, structure: AnalysedRootContainer<P>?) {
        structure?.let { logStructure(sessionId, it) }
        logSession(sessionId, session)
      }
    }
  }

  companion object {
    val DOUBLE: MLSessionLoggingStrategy<Double> = SessionAsMultipleEventsLoggingStrategy(DoubleEventField("prediction", null)) { it }
  }
}

private val COMPONENT_NAME_MAIN_INSTANCE = { levelI: Int, tierName: String -> "main_instance.$levelI.$tierName" }
private val COMPONENT_NAME_ADDITIONAL_INSTANCE = { levelI: Int, tierName: String -> "additional_instance.$levelI.$tierName" }
private val COMPONENT_NAME_BRANCHING = { levelI: Int -> "branching_level.$levelI" }
private const val COMPONENT_NAME_COMPLEX_ROOT = "complex_root"
private const val COMPONENT_NAME_SOLITARY_LEAF = "solitary_leaf"
private const val COMPONENT_NAME_LEAF = "leaf"
private const val COMPONENT_NAME_SESSION = "session"

private val FIELD_SESSION_ID = IntEventField("session_id", null)
private val FIELD_LOCATION_IN_TREE = IntListEventField("location_in_tree", null)
private val FIELD_ADDITIONAL_INSTANCES = IntEventField("n_additional_instances", null)
private val FIELD_MAIN_INSTANCES = IntEventField("n_main_instances", null)
private val FIELD_N_NESTED = IntEventField("n_nested", null)

private const val FIELD_NAME_DESCRIPTION = "description"
private const val FIELD_NAME_ANALYSIS = "analysis"


private object ComponentMainInstance {
  fun register(eventRegister: MLSessionComponentRegister, levelI: Int, tier: Tier<*>, scheme: AnalysedTierScheme) = run {
    val objectDescription = TierDescriptionFields(scheme.description)
    val objectAnalysis = FeatureSet(scheme.analysis)
    val fieldDescription = ObjectEventField(FIELD_NAME_DESCRIPTION, null, objectDescription)
    val fieldAnalysis = ObjectEventField(FIELD_NAME_ANALYSIS, null, objectAnalysis)

    val event = eventRegister.registerComponent(COMPONENT_NAME_MAIN_INSTANCE(levelI, tier.name), listOf(
      FIELD_SESSION_ID,
      FIELD_LOCATION_IN_TREE,
      fieldDescription,
      fieldAnalysis)
    )

    return@run { sessionId: Int, locationInTree: List<Int>, descriptionPartition: DescriptionPartition, analysis: Set<Feature> ->
      event.log(buildList {
        add(FIELD_SESSION_ID with sessionId)
        add(FIELD_LOCATION_IN_TREE with locationInTree)
        add(fieldDescription with objectDescription.buildObjectEventData(descriptionPartition))
        add(fieldAnalysis with objectAnalysis.toObjectEventData(analysis))
      })
    }
  }
}

private object ComponentAdditionalInstance {
  fun register(eventRegister: MLSessionComponentRegister, levelI: Int, tier: Tier<*>, scheme: DescribedTierScheme) = run {
    val objectDescription = TierDescriptionFields(scheme.description)
    val fieldDescription = ObjectEventField(FIELD_NAME_DESCRIPTION, null, objectDescription)

    val event = eventRegister.registerComponent(
      COMPONENT_NAME_ADDITIONAL_INSTANCE(levelI, tier.name),
      listOf(
        FIELD_SESSION_ID,
        FIELD_LOCATION_IN_TREE,
        fieldDescription)
    )

    return@run { sessionId: Int, locationInTree: List<Int>, descriptionPartition: DescriptionPartition ->
      event.log(buildList {
        add(FIELD_SESSION_ID with sessionId)
        add(FIELD_LOCATION_IN_TREE with locationInTree)
        add(fieldDescription with objectDescription.buildObjectEventData(descriptionPartition))
      })
    }
  }
}

typealias ComplexRoot<P> = SessionTree.ComplexRoot<Unit, AnalysedTierData, P>
typealias Branching<P> = SessionTree.Branching<Unit, AnalysedTierData, P>
typealias ChildrenContainer<P> = SessionTree.ChildrenContainer<Unit, AnalysedTierData, P>
typealias PredictionContainer<P> = SessionTree.PredictionContainer<Unit, AnalysedTierData, P>
typealias SolitaryLeaf<P> = SessionTree.SolitaryLeaf<Unit, AnalysedTierData, P>

private object ComponentLevelData {
  fun register(eventRegister: MLSessionComponentRegister, levelI: Int, declaration: AnalysedLevelScheme) = run {
    val mainTierLoggers = declaration.main.entries.associate { (tier, tierScheme) ->
      tier to ComponentMainInstance.register(eventRegister, levelI, tier, tierScheme)
    }
    val additionalTierLoggers = declaration.additional.entries.associate { (tier, tierScheme) ->
      tier to ComponentAdditionalInstance.register(eventRegister, levelI, tier, tierScheme)
    }
    return@run { sessionId: Int, locationInTree: List<Int>, levelData: LevelData<AnalysedTierData> ->
      levelData.mainInstances.forEach { (tierInstance, tierData) ->
        val tierLogger = mainTierLoggers.getValue(tierInstance.tier)
        tierLogger(sessionId, locationInTree, tierData.description, tierData.analysis)
      }
      levelData.additionalInstances.forEach { (tierInstance, tierData) ->
        val tierLogger = additionalTierLoggers.getValue(tierInstance.tier)
        tierLogger(sessionId, locationInTree, tierData.description)
      }
    }
  }
}

private object ComponentNode {
  fun <P : Any> register(eventRegister: MLSessionComponentRegister, componentName: String,
                         additionalFields: Array<EventField<*>>,
                         buildAdditionalFields: (AnalysedSessionTree<P>) -> Array<EventPair<*>>
  ): (Int, List<Int>, AnalysedSessionTree<P>) -> Unit {
    val event = eventRegister.registerComponent(componentName,
                                                listOf(FIELD_SESSION_ID,
                                                       FIELD_LOCATION_IN_TREE,
                                                       FIELD_MAIN_INSTANCES,
                                                       FIELD_ADDITIONAL_INSTANCES,
                                                       FIELD_N_NESTED,
                                                       *additionalFields))

    return { sessionId: Int, locationInTree: List<Int>, sessionTree: AnalysedSessionTree<P> ->
      event.log(buildList {
        add(FIELD_SESSION_ID with sessionId)
        add(FIELD_LOCATION_IN_TREE with locationInTree)
        add(FIELD_MAIN_INSTANCES with sessionTree.levelData.mainInstances.size)
        add(FIELD_ADDITIONAL_INSTANCES with sessionTree.levelData.additionalInstances.size)
        if (sessionTree is ChildrenContainer<P>)
          add(FIELD_N_NESTED with sessionTree.children.size)
        addAll(buildAdditionalFields(sessionTree))
      })
    }
  }
}

private object ComponentBranching {
  fun <P : Any, F> register(
    fieldPrediction: EventField<F>,
    serializePrediction: (P?) -> F?,
    eventRegister: MLSessionComponentRegister,
    levelI: Int,
    levelsScheme: List<AnalysedLevelScheme>
  ): (Int, List<Int>, AnalysedSessionTree<P>) -> Unit {
    val nodeLogger = ComponentNode.register<P>(eventRegister, COMPONENT_NAME_BRANCHING(levelI), emptyArray()) { emptyArray() }
    val levelDataLogger = ComponentLevelData.register(eventRegister, levelI, levelsScheme[levelI])
    val nextLevelLogger = if (levelI + 1 == levelsScheme.size - 1)
      ComponentLeaf.register(fieldPrediction, serializePrediction, eventRegister, levelsScheme)
    else
      register(fieldPrediction, serializePrediction, eventRegister, levelI + 1, levelsScheme)

    return { sessionId: Int, locationInTree: List<Int>, sessionTree: AnalysedSessionTree<P> ->
      require(sessionTree is Branching<P>)
      nodeLogger(sessionId, locationInTree, sessionTree)
      levelDataLogger(sessionId, locationInTree, sessionTree.levelData)
      sessionTree.children.withIndex().forEach { (idx, children) ->
        nextLevelLogger(sessionId, locationInTree + idx, children)
      }
    }
  }
}

private object ComponentLeaf {
  fun <P : Any, F> register(
    fieldPrediction: EventField<F>,
    serializePrediction: (P?) -> F?,
    eventRegister: MLSessionComponentRegister,
    levelsScheme: List<AnalysedLevelScheme>,
  ): (Int, List<Int>, AnalysedSessionTree<P>) -> Unit {
    val levelDataLogger = ComponentLevelData.register(eventRegister, 0, levelsScheme.last())
    val nodeLogger = ComponentNode.register<P>(eventRegister, COMPONENT_NAME_LEAF, arrayOf(fieldPrediction)) { sessionTree ->
      require(sessionTree is PredictionContainer<P>)
      val serializedPrediction = serializePrediction(sessionTree.prediction)
      serializedPrediction?.let { arrayOf(fieldPrediction with it) } ?: emptyArray()
    }

    return { sessionId: Int, locationInTree: List<Int>, sessionTree: AnalysedSessionTree<P> ->
      nodeLogger(sessionId, locationInTree, sessionTree)
      levelDataLogger(sessionId, locationInTree, sessionTree.levelData)
    }
  }
}

private object ComponentComplexRoot {
  fun <P : Any, F> register(
    fieldPrediction: EventField<F>,
    serializePrediction: (P?) -> F?,
    eventRegister: MLSessionComponentRegister,
    levelsScheme: List<AnalysedLevelScheme>
  ): (Int, AnalysedRootContainer<P>) -> Unit {
    val nodeLogger = ComponentNode.register<P>(eventRegister, COMPONENT_NAME_COMPLEX_ROOT, emptyArray()) { emptyArray() }
    val levelDataLogger = ComponentLevelData.register(eventRegister, 0, levelsScheme.first())
    val nextLevelLogger = if (levelsScheme.size == 2)
      ComponentLeaf.register(fieldPrediction, serializePrediction, eventRegister, levelsScheme)
    else
      ComponentBranching.register(fieldPrediction, serializePrediction, eventRegister, 1, levelsScheme)

    return { sessionId: Int, sessionTree: AnalysedRootContainer<P> ->
      require(sessionTree is ComplexRoot<P>)
      nodeLogger(sessionId, emptyList(), sessionTree)
      levelDataLogger(sessionId, emptyList(), sessionTree.levelData)
      sessionTree.children.withIndex().forEach { (idx, children) ->
        nextLevelLogger(sessionId, listOf(idx), children)
      }
    }
  }
}

private object ComponentSolitaryLeaf {
  fun <P : Any, F> register(
    fieldPrediction: EventField<F>,
    serializePrediction: (P?) -> F?,
    eventRegister: MLSessionComponentRegister,
    levelsScheme: List<AnalysedLevelScheme>
  ): (Int, AnalysedRootContainer<P>) -> Unit {
    val nodeLogger = ComponentNode.register<P>(eventRegister, COMPONENT_NAME_SOLITARY_LEAF, arrayOf(fieldPrediction)) { sessionTree ->
      require(sessionTree is PredictionContainer<P>)
      val serializedPrediction = serializePrediction(sessionTree.prediction)
      serializedPrediction?.let { arrayOf(fieldPrediction with it) } ?: emptyArray()
    }
    val levelDataLogger = ComponentLevelData.register(eventRegister, 0, levelsScheme.first())

    return { sessionId: Int, sessionTree: AnalysedRootContainer<P> ->
      require(sessionTree is SolitaryLeaf<P>)
      nodeLogger(sessionId, emptyList(), sessionTree)
      levelDataLogger(sessionId, emptyList(), sessionTree.levelData)
    }
  }
}

private class ComponentSomeAnalysis(private val componentName: String) {
  fun register(
    eventRegister: MLSessionComponentRegister,
    analysisDeclaration: List<EventField<*>>
  ): (Int, List<EventPair<*>>) -> Unit {
    val event = eventRegister.registerComponent(componentName,
                                                listOf(FIELD_SESSION_ID,
                                                       *analysisDeclaration.toTypedArray()))
    return { sessionId, analysis ->
      event.log(listOf(
        FIELD_SESSION_ID with sessionId,
        *analysis.toTypedArray()
      ))
    }
  }
}
