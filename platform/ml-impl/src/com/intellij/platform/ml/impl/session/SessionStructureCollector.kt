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
class RootCollector<M : MLModel<P>, P : Any>(
  apiPlatform: MLApiPlatform,
  levelsTiers: List<LevelTiers>,
  descriptionComputer: DescriptionComputer,
  notUsedFeaturesSelectors: PerTier<FeatureSelector>,
  levelMainEnvironment: Environment,
  levelAdditionalTiers: Set<Tier<*>>,
  private val mlModel: M
) : NestableStructureCollector<SessionTree.ComplexRoot<M, DescribedLevel, P>, M, P>() {
  override val levelDescriptor = LevelDescriptor(apiPlatform, descriptionComputer, mlModel.knownFeatures, notUsedFeaturesSelectors)
  override val levelPositioning = LevelPositioning.superior(levelsTiers, levelDescriptor, levelMainEnvironment, levelAdditionalTiers)

  init {
    validateSuperiorCollector(levelsTiers, levelMainEnvironment, levelAdditionalTiers, mlModel, notUsedFeaturesSelectors)
  }

  override fun createTree(thisLevel: DescribedLevel,
                          collectedNestedStructureTrees: List<DescribedSessionTree<M, P>>): SessionTree.ComplexRoot<M, DescribedLevel, P> {
    return SessionTree.ComplexRoot(mlModel, thisLevel, collectedNestedStructureTrees)
  }
}

@ApiStatus.Internal
class SolitaryLeafCollector<M : MLModel<P>, P : Any>(
  apiPlatform: MLApiPlatform,
  levelScheme: LevelTiers,
  descriptionComputer: DescriptionComputer,
  notUsedFeaturesSelectors: PerTier<FeatureSelector>,
  levelMainEnvironment: Environment,
  levelAdditionalTiers: Set<Tier<*>>,
  private val mlModel: M
) : PredictionCollector<SessionTree.SolitaryLeaf<M, DescribedLevel, P>, M, P>() {
  override val levelDescriptor = LevelDescriptor(apiPlatform, descriptionComputer, mlModel.knownFeatures, notUsedFeaturesSelectors)
  override val levelPositioning = LevelPositioning.superior(listOf(levelScheme), levelDescriptor, levelMainEnvironment,
                                                            levelAdditionalTiers)

  init {
    validateSuperiorCollector(listOf(levelScheme), levelMainEnvironment, levelAdditionalTiers, mlModel, notUsedFeaturesSelectors)
  }

  override fun createTree(thisLevel: DescribedLevel, prediction: P?): SessionTree.SolitaryLeaf<M, DescribedLevel, P> {
    return SessionTree.SolitaryLeaf(mlModel, levelPositioning.thisLevel, prediction)
  }
}

@ApiStatus.Internal
class BranchingCollector<M : MLModel<P>, P : Any>(
  override val levelDescriptor: LevelDescriptor,
  override val levelPositioning: LevelPositioning
) : NestableStructureCollector<SessionTree.Branching<M, DescribedLevel, P>, M, P>() {
  override fun createTree(thisLevel: DescribedLevel,
                          collectedNestedStructureTrees: List<DescribedSessionTree<M, P>>): SessionTree.Branching<M, DescribedLevel, P> {
    return SessionTree.Branching(thisLevel, collectedNestedStructureTrees)
  }
}

@ApiStatus.Internal
abstract class PredictionCollector<T : SessionTree.PredictionContainer<M, DescribedLevel, P>, M : MLModel<P>, P : Any> : StructureCollector<T, M, P>() {
  private var predictionSubmitted = false
  private var submittedPrediction: P? = null

  abstract fun createTree(thisLevel: DescribedLevel, prediction: P?): T

  val usableDescription: PerTier<Set<Feature>>
    get() = levelPositioning.levels.extractDescriptionForModel()

  fun submitPrediction(prediction: P?) {
    require(!predictionSubmitted)
    submittedPrediction = prediction
    predictionSubmitted = true
    submitTreeToHandlers(createTree(levelPositioning.thisLevel, submittedPrediction))
  }

  private fun PerTierInstance<DescribedTierData>.extractDescriptionForModel(): PerTier<Set<Feature>> {
    return this.entries.associate { (tierInstance, data) ->
      tierInstance.tier to data.description.declared.used + data.description.nonDeclared.used
    }
  }

  private fun DescribedLevel.extractDescriptionForModel(): PerTier<Set<Feature>> {
    val mainDescription = this.main.extractDescriptionForModel()
    val additionalDescription = this.additional.extractDescriptionForModel()
    return listOf(mainDescription + additionalDescription).joinByUniqueTier()
  }

  private fun Iterable<DescribedLevel>.extractDescriptionForModel(): PerTier<Set<Feature>> {
    return this.map { it.extractDescriptionForModel() }.joinByUniqueTier()
  }
}

@ApiStatus.Internal
class LeafCollector<M : MLModel<P>, P : Any>(
  override val levelDescriptor: LevelDescriptor,
  override val levelPositioning: LevelPositioning
) : PredictionCollector<SessionTree.Leaf<M, DescribedLevel, P>, M, P>() {

  override fun createTree(thisLevel: DescribedLevel, prediction: P?): SessionTree.Leaf<M, DescribedLevel, P> {
    return SessionTree.Leaf(levelPositioning.thisLevel, prediction)
  }
}

private fun <M : MLModel<P>, P : Any> validateSuperiorCollector(levelsTiers: List<LevelTiers>,
                                                                levelMainEnvironment: Environment,
                                                                levelAdditionalTiers: Set<Tier<*>>,
                                                                mlModel: M,
                                                                notUsedDescriptionSelectors: PerTier<FeatureSelector>) {
  val allTiers = (levelsTiers.flatMap { it.main + it.additional } + levelMainEnvironment.tiers + levelAdditionalTiers).toSet()
  val usedFeaturesSelectors = mlModel.knownFeatures

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
  val lowerTiers: List<LevelTiers>,
  val thisLevel: DescribedLevel,
) {
  val levels: List<DescribedLevel> = upperLevels + thisLevel

  fun nestNextLevel(
    levelDescriptor: LevelDescriptor,
    nextLevelMainEnvironment: Environment,
    nextLevelAdditionalTiers: Set<Tier<*>>
  ): LevelPositioning {
    return LevelPositioning(
      upperLevels = upperLevels + thisLevel,
      lowerTiers = lowerTiers.drop(1),
      thisLevel = levelDescriptor.describe(upperLevels, nextLevelMainEnvironment, nextLevelAdditionalTiers)
    )
  }

  companion object {
    fun superior(levelsTiers: List<LevelTiers>,
                 levelDescriptor: LevelDescriptor,
                 levelMainEnvironment: Environment,
                 levelAdditionalTiers: Set<Tier<*>>): LevelPositioning {
      return LevelPositioning(emptyList(), levelsTiers.drop(1),
                              levelDescriptor.describe(emptyList(), levelMainEnvironment, levelAdditionalTiers))
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
abstract class NestableStructureCollector<T : DescribedChildrenContainer<M, P>, M : MLModel<P>, P : Any> : StructureCollector<T, M, P>() {
  private val nestedSessionsStructures: MutableList<CompletableFuture<DescribedSessionTree<M, P>>> = mutableListOf()
  private var nestingFinished = false

  fun nestBranch(levelMainEnvironment: Environment, levelAdditionalTiers: Set<Tier<*>>): BranchingCollector<M, P> {
    verifyNestedLevelEnvironment(levelMainEnvironment, levelAdditionalTiers)
    return BranchingCollector<M, P>(levelDescriptor,
                                    levelPositioning.nestNextLevel(levelDescriptor, levelMainEnvironment, levelAdditionalTiers))
      .also { it.trackCollectedStructure() }
  }

  fun nestPrediction(levelMainEnvironment: Environment, levelAdditionalTiers: Set<Tier<*>>): LeafCollector<M, P> {
    verifyNestedLevelEnvironment(levelMainEnvironment, levelAdditionalTiers)
    return LeafCollector<M, P>(levelDescriptor, levelPositioning.nestNextLevel(levelDescriptor, levelMainEnvironment, levelAdditionalTiers))
      .also { it.trackCollectedStructure() }
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
    val collectedNestedStructureTrees: List<SessionTree<M, DescribedLevel, P>> = nestedSessionsStructures
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
