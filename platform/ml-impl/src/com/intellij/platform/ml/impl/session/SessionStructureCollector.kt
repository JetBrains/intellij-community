// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session

import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.DescriptionComputer
import com.intellij.platform.ml.impl.FeatureSelector
import com.intellij.platform.ml.impl.LevelTiers
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.model.MLModel
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class RootCollector<M : MLModel<P>, P : Any> private constructor(
  initializationCallParameters: Environment,
  callParameters: List<Set<Tier<*>>>,
  notUsedFeaturesSelectors: PerTier<FeatureFilter>,
  levelMainEnvironment: Environment,
  levelAdditionalTiers: Set<Tier<*>>,
  private val mlModel: M,
  levelsTiers: List<LevelTiers>,
  usedFeaturesSelectors: PerTier<FeatureSelector>,
  override val levelDescriptor: LevelDescriptor,
  override val levelPositioning: LevelPositioning
) : NestableStructureCollector<SessionTree.ComplexRoot<M, DescribedTierData, P>, M, P>(usedFeaturesSelectors) {

  init {
    validateSuperiorCollector(initializationCallParameters, callParameters.first(), levelsTiers, levelMainEnvironment, levelAdditionalTiers, mlModel, notUsedFeaturesSelectors)
  }

  override fun createTree(thisLevel: DescribedLevel,
                          collectedNestedStructureTrees: List<DescribedSessionTree<M, P>>): SessionTree.ComplexRoot<M, DescribedTierData, P> {
    return SessionTree.ComplexRoot(mlModel, thisLevel, collectedNestedStructureTrees)
  }

  companion object {
    suspend fun <M : MLModel<P>, P : Any> build(
      initializationCallParameters: Environment,
      callParameters: List<Set<Tier<*>>>,
      descriptionComputer: DescriptionComputer,
      notUsedFeaturesSelectors: PerTier<FeatureFilter>,
      levelMainEnvironment: Environment,
      levelAdditionalTiers: Set<Tier<*>>,
      mlModel: M,
      apiPlatform: MLApiPlatform,
      levelsTiers: List<LevelTiers>,
      usedFeaturesSelectors: PerTier<FeatureSelector>
    ): RootCollector<M, P> {
      val levelDescriptor = LevelDescriptor(apiPlatform, descriptionComputer, mlModel.knownFeatures, notUsedFeaturesSelectors)
      val levelPositioning = LevelPositioning.superior(initializationCallParameters, callParameters, levelsTiers, levelMainEnvironment, levelDescriptor, levelAdditionalTiers)
      return RootCollector(initializationCallParameters, callParameters, notUsedFeaturesSelectors, levelMainEnvironment, levelAdditionalTiers, mlModel, levelsTiers, usedFeaturesSelectors, levelDescriptor, levelPositioning)
    }
  }
}

@ApiStatus.Internal
class SolitaryLeafCollector<M : MLModel<P>, P : Any> private constructor(
  initializationCallParameters: Environment,
  callParameters: List<Set<Tier<*>>>,
  notUsedFeaturesSelectors: PerTier<FeatureFilter>,
  levelMainEnvironment: Environment,
  levelAdditionalTiers: Set<Tier<*>>,
  private val mlModel: M,
  levelScheme: LevelTiers,
  usedFeaturesSelectors: PerTier<FeatureSelector>,
  override val levelDescriptor: LevelDescriptor,
  override val levelPositioning: LevelPositioning
) : PredictionCollector<SessionTree.SolitaryLeaf<M, DescribedTierData, P>, M, P>(usedFeaturesSelectors) {

  init {
    validateSuperiorCollector(initializationCallParameters, callParameters.first(), listOf(levelScheme), levelMainEnvironment, levelAdditionalTiers, mlModel, notUsedFeaturesSelectors)
  }

  override fun createTree(thisLevel: DescribedLevel, prediction: P?): SessionTree.SolitaryLeaf<M, DescribedTierData, P> {
    return SessionTree.SolitaryLeaf(mlModel, levelPositioning.thisLevel, prediction)
  }

  companion object {
    suspend fun <M : MLModel<P>, P : Any> build(
      initializationCallParameters: Environment,
      callParameters: List<Set<Tier<*>>>,
      descriptionComputer: DescriptionComputer,
      notUsedFeaturesSelectors: PerTier<FeatureFilter>,
      levelMainEnvironment: Environment,
      levelAdditionalTiers: Set<Tier<*>>,
      mlModel: M,
      apiPlatform: MLApiPlatform,
      levelScheme: LevelTiers,
      usedFeaturesSelectors: PerTier<FeatureSelector>
    ): SolitaryLeafCollector<M, P> {
      val levelDescriptor = LevelDescriptor(apiPlatform, descriptionComputer, mlModel.knownFeatures, notUsedFeaturesSelectors)
      val levelPositioning = LevelPositioning.superior(initializationCallParameters, callParameters, listOf(levelScheme), levelMainEnvironment,
                                                       levelDescriptor, levelAdditionalTiers)
      return SolitaryLeafCollector(initializationCallParameters, callParameters, notUsedFeaturesSelectors, levelMainEnvironment, levelAdditionalTiers, mlModel, levelScheme, usedFeaturesSelectors, levelDescriptor, levelPositioning)
    }
  }
}

@ApiStatus.Internal
class BranchingCollector<M : MLModel<P>, P : Any>(
  override val levelDescriptor: LevelDescriptor,
  override val levelPositioning: LevelPositioning,
  usedFeaturesSelectors: PerTier<FeatureSelector>
) : NestableStructureCollector<SessionTree.Branching<M, DescribedTierData, P>, M, P>(usedFeaturesSelectors) {
  override fun createTree(thisLevel: DescribedLevel,
                          collectedNestedStructureTrees: List<DescribedSessionTree<M, P>>): SessionTree.Branching<M, DescribedTierData, P> {
    return SessionTree.Branching(thisLevel, collectedNestedStructureTrees)
  }
}

@ApiStatus.Internal
abstract class PredictionCollector<T : SessionTree.PredictionContainer<M, DescribedTierData, P>, M : MLModel<P>, P : Any>(
  private val usedFeaturesSelectors: PerTier<FeatureSelector>
) : StructureCollector<T, M, P>() {
  private var predictionSubmitted = false
  private var submittedPrediction: P? = null

  abstract fun createTree(thisLevel: DescribedLevel, prediction: P?): T

  val usableDescription: PerTier<Set<Feature>>
    get() = levelPositioning.levels.extractDescriptionForModel()

  val callParameters: List<Environment>
    get() = levelPositioning.callParameters

  fun submitPrediction(prediction: P?) {
    require(!predictionSubmitted)
    submittedPrediction = prediction
    predictionSubmitted = true
    submitTreeToHandlers(createTree(levelPositioning.thisLevel, submittedPrediction))
  }

  private fun PerTierInstance<DescribedTierData>.extractDescriptionForModel(): PerTier<Set<Feature>> {
    return this.entries.filter { it.key.tier in usedFeaturesSelectors }.associate { (tierInstance, data) ->
      tierInstance.tier to data.description.declared.used + data.description.nonDeclared.used
    }
  }

  private fun DescribedLevel.extractDescriptionForModel(): PerTier<Set<Feature>> {
    val mainDescription = this.mainInstances.extractDescriptionForModel()
    val additionalDescription = this.additionalInstances.extractDescriptionForModel()
    return listOf(mainDescription + additionalDescription).joinByUniqueTier()
  }

  private fun Iterable<DescribedLevel>.extractDescriptionForModel(): PerTier<Set<Feature>> {
    return this.map { it.extractDescriptionForModel() }.joinByUniqueTier()
  }
}

@ApiStatus.Internal
class LeafCollector<M : MLModel<P>, P : Any>(
  override val levelDescriptor: LevelDescriptor,
  override val levelPositioning: LevelPositioning,
  usedFeaturesSelectors: PerTier<FeatureSelector>
) : PredictionCollector<SessionTree.Leaf<M, DescribedTierData, P>, M, P>(usedFeaturesSelectors) {

  override fun createTree(thisLevel: DescribedLevel, prediction: P?): SessionTree.Leaf<M, DescribedTierData, P> {
    return SessionTree.Leaf(levelPositioning.thisLevel, prediction)
  }
}

private fun <M : MLModel<P>, P : Any> validateSuperiorCollector(callParameters: Environment,
                                                                expectedCallParameters: Set<Tier<*>>,
                                                                levelsTiers: List<LevelTiers>,
                                                                levelMainEnvironment: Environment,
                                                                levelAdditionalTiers: Set<Tier<*>>,
                                                                mlModel: M,
                                                                notUsedDescriptionSelectors: PerTier<FeatureFilter>) {
  val allTiers = (levelsTiers.flatMap { it.main + it.additional } + levelMainEnvironment.tiers + levelAdditionalTiers).toSet()
  val usedFeaturesSelectors = mlModel.knownFeatures

  require(callParameters.tiers == expectedCallParameters) {
    """
      Invalid call parameters passed.
      Missing: ${expectedCallParameters - callParameters.tiers}
      Redundant: ${callParameters.tiers - expectedCallParameters}
    """.trimMargin()
  }

  require(allTiers.containsAll(usedFeaturesSelectors.keys)) {
    "ML Model uses tiers that are not main or additional: ${usedFeaturesSelectors.keys - allTiers}"
  }
  require(notUsedDescriptionSelectors.keys == allTiers) {
    """
      Not used description's tiers must be same as the task tiersAll tiers
      Missing: ${allTiers - notUsedDescriptionSelectors.keys}
      Redundant: ${notUsedDescriptionSelectors.keys - allTiers}
    """.trimIndent()
  }
}

@ApiStatus.Internal
data class LevelPositioning(
  val upperLevels: List<DescribedLevel>,
  val upperCallParameters: List<Environment>,
  val lowerTiers: List<LevelTiers>,
  val lowerCallParameters: List<Set<Tier<*>>>,
  val thisLevel: DescribedLevel,
  val thisCallParameters: Environment
) {
  val levels: List<DescribedLevel> = upperLevels + thisLevel

  val callParameters = upperCallParameters + thisCallParameters

  suspend fun nestNextLevel(
    nextLevelCallParameters: Environment,
    nextLevelMainEnvironment: Environment,
    nextLevelAdditionalTiers: Set<Tier<*>>,
    levelDescriptor: LevelDescriptor
  ): LevelPositioning {
    return LevelPositioning(
      upperLevels = upperLevels + thisLevel,
      upperCallParameters = upperCallParameters + thisCallParameters,
      lowerTiers = lowerTiers.drop(1),
      lowerCallParameters = lowerCallParameters.drop(1),
      thisLevel = levelDescriptor.describe(nextLevelCallParameters, nextLevelMainEnvironment, levels, nextLevelAdditionalTiers),
      thisCallParameters = nextLevelCallParameters
    )
  }

  companion object {
    suspend fun superior(initializationCallParameters: Environment,
                         callParameters: List<Set<Tier<*>>>,
                         levelsTiers: List<LevelTiers>,
                         levelMainEnvironment: Environment,
                         levelDescriptor: LevelDescriptor,
                         levelAdditionalTiers: Set<Tier<*>>): LevelPositioning {
      return LevelPositioning(
        upperLevels = emptyList(),
        upperCallParameters = emptyList(),
        lowerTiers = levelsTiers.drop(1),
        lowerCallParameters = callParameters.drop(1),
        thisLevel = levelDescriptor.describe(initializationCallParameters, levelMainEnvironment, emptyList(), levelAdditionalTiers),
        thisCallParameters = initializationCallParameters
      )
    }
  }
}

@ApiStatus.Internal
sealed class StructureCollector<T : DescribedSessionTree<M, P>, M : MLModel<P>, P : Any> {
  protected abstract val levelDescriptor: LevelDescriptor
  abstract val levelPositioning: LevelPositioning

  private val sessionTreeHandlers: MutableList<SessionTreeHandler<in T, M, P>> = mutableListOf()

  fun handleCollectedTree(handler: SessionTreeHandler<in T, M, P>) {
    sessionTreeHandlers.add(handler)
  }

  protected fun submitTreeToHandlers(sessionTree: T) {
    sessionTreeHandlers.forEach { it.handleTree(sessionTree) }
  }
}

@ApiStatus.Internal
abstract class NestableStructureCollector<T : DescribedChildrenContainer<M, P>, M : MLModel<P>, P : Any>(
  private val usedFeaturesSelectors: PerTier<FeatureSelector>
) : StructureCollector<T, M, P>() {
  private val nestedSessionsStructures: MutableList<CompletableFuture<DescribedSessionTree<M, P>>> = mutableListOf()
  private var nestingFinished = false

  suspend fun nestBranch(callParameters: Environment, levelMainEnvironment: Environment, levelAdditionalTiers: Set<Tier<*>>): BranchingCollector<M, P> {
    verifyNestedLevelEnvironment(levelMainEnvironment, levelAdditionalTiers)
    return BranchingCollector<M, P>(levelDescriptor,
                                    levelPositioning.nestNextLevel(callParameters, levelMainEnvironment, levelAdditionalTiers, levelDescriptor),
                                    usedFeaturesSelectors)
      .also { it.trackCollectedStructure() }
  }

  suspend fun nestPrediction(callParameters: Environment, levelMainEnvironment: Environment, levelAdditionalTiers: Set<Tier<*>>): LeafCollector<M, P> {
    verifyNestedLevelEnvironment(levelMainEnvironment, levelAdditionalTiers)
    return LeafCollector<M, P>(
      levelDescriptor,
      levelPositioning.nestNextLevel(callParameters, levelMainEnvironment, levelAdditionalTiers, levelDescriptor),
      usedFeaturesSelectors
    ).also { it.trackCollectedStructure() }
  }

  fun onLastNestedCollectorCreated() {
    require(!nestingFinished)
    nestingFinished = true
    maybeSubmitStructure()
  }

  private fun <K : DescribedSessionTree<M, P>> StructureCollector<K, M, P>.trackCollectedStructure() {
    val collectedNestedTreeContainer = CompletableFuture<DescribedSessionTree<M, P>>()
    nestedSessionsStructures += collectedNestedTreeContainer
    this.handleCollectedTree {
      collectedNestedTreeContainer.complete(it)
    }
  }

  private fun maybeSubmitStructure() {
    val collectedNestedStructureTrees: List<SessionTree<M, DescribedTierData, P>> = nestedSessionsStructures
      .filter { it.isDone }
      .map { it.get() }

    if (nestingFinished && collectedNestedStructureTrees.size == nestedSessionsStructures.size) {
      val describedSessionTree = createTree(levelPositioning.thisLevel, collectedNestedStructureTrees)
      submitTreeToHandlers(describedSessionTree)
    }
  }

  protected abstract fun createTree(thisLevel: DescribedLevel, collectedNestedStructureTrees: List<DescribedSessionTree<M, P>>): T

  private fun verifyNestedLevelEnvironment(levelMainEnvironment: Environment, levelAdditionalTiers: Set<Tier<*>>) {
    require(levelPositioning.lowerTiers.first().main == levelMainEnvironment.tiers)
    require(levelPositioning.lowerTiers.first().additional == levelAdditionalTiers)
  }
}
