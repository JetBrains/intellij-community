// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.approach

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ml.*
import com.intellij.platform.ml.ScopeEnvironment.Companion.narrowedTo
import com.intellij.platform.ml.impl.*
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.getDescriptorsOfTiers
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.getJoinedListenerForTask
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener
import com.intellij.platform.ml.impl.session.*
import org.jetbrains.annotations.ApiStatus

/**
 * The main way to apply classical machine learning approaches: run the ML model, collect the logs, retrain the model, repeat.
 *
 * @param task The task that is solved by this approach.
 * @param apiPlatform The platform, that the approach will be running within.
 */
@ApiStatus.Internal
abstract class LogDrivenModelInference<M : MLModel<P>, P : Any>(
  override val task: MLTask<P>,
  override val apiPlatform: MLApiPlatform
) : MLTaskApproach<P> {
  /**
   * The method that is used to analyze sessions.
   *
   * [StructureAndModelAnalysis] is currently used analysis method
   * that is dedicated to analyze session's tree-like structure,
   * and the ML model.
   */
  abstract val analysisMethod: AnalysisMethod<M, P>

  /**
   * Provides an ML model to use during session's lifetime.
   */
  abstract val mlModelProvider: MLModel.Provider<M, P>

  /**
   * Declares features, that are not used by the ML model, but must be computed anyway,
   * so they make it to logs.
   *
   * A feature cannot be simultaneously declared as "not used description" and as used by the [mlModelProvider]'s
   * provided model.
   * If a feature is not declared as "not used but still computed" or as "used by the model", then it will be computed.
   *
   * It must contain explicitly declared selectors for each tier used in [task], as well as in [additionallyDescribedTiers].
   *
   * @param callParameters Contains any additional parameters that you passed in [com.intellij.platform.ml.impl.MLTaskApproach.startMLSession]
   * The tier set is equal to the one that was declared in [com.intellij.platform.ml.impl.MLTask.callParameters]'s first level.
   * @param mlModel The model that was acquired by the [mlModelProvider]
   */
  abstract fun getNotUsedDescription(callParameters: Environment, mlModel: M): PerTier<FeatureFilter>

  /**
   * Performs description's computation.
   * Could perform caching mechanisms to avoid recomputing features every time.
   */
  abstract val descriptionComputer: DescriptionComputer

  /**
   * Tiers that do not make a part of te [task], but they could be described and passed to the ML model.
   *
   * The size of this list must correspond to the number of levels in the solved [task].
   */
  abstract val additionallyDescribedTiers: List<Set<Tier<*>>>

  private val levels: List<LevelTiers> by lazy {
    (task.levels zip additionallyDescribedTiers).map { LevelSignature(it.first, it.second) }
  }

  private val approachValidation: Unit by lazy { validateApproach() }

  override suspend fun startSession(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
    return startSessionMonitoring(callParameters, permanentSessionEnvironment)
  }

  private suspend fun startSessionMonitoring(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
    val approachListener = apiPlatform.getJoinedListenerForTask<M, P>(this, permanentSessionEnvironment)
    try {
      return acquireModelAndStartSession(callParameters, permanentSessionEnvironment, approachListener)
    }
    catch (e: Throwable) {
      approachListener.onFailedToStartSessionWithException(e)
      return Session.StartOutcome.UncaughtException(e)
    }
  }

  private suspend fun acquireModelAndStartSession(unsafeCallParameters: Environment,
                                          permanentSessionEnvironment: Environment,
                                          approachListener: MLApproachListener<M, P>): Session.StartOutcome<P> {
    approachValidation

    val callParameters = unsafeCallParameters.narrowedTo(task.callParameters.first())
    val extendedPermanentSessionEnvironment = Environment.joined(callParameters, permanentSessionEnvironment)

    val mlModel: M = run {
      val nullableMlModel = mlModelProvider.provideModel(callParameters, extendedPermanentSessionEnvironment, levels)
      if (nullableMlModel == null) {
        val failure = ModelNotAcquiredOutcome<P>()
        approachListener.onFailedToStartSession(failure)
        return failure
      }
      nullableMlModel
    }

    var sessionListener: MLSessionListener<M, P>? = null

    val analyseThenLogStructure = SessionTreeHandler<DescribedRootContainer<M, P>, M, P> { treeRoot ->
      sessionListener?.onSessionDescriptionFinished(treeRoot)
      analysisMethod.analyseTree(treeRoot).thenApplyAsync { analysedSession ->
        sessionListener?.onSessionAnalysisFinished(analysedSession)
      }.exceptionally {
        thisLogger().error(it)
      }
    }

    val notUsedDescription = getNotUsedDescription(callParameters, mlModel)
    validateNotUsedDescription(notUsedDescription)

    val session = if (levels.size == 1) {
      val collector = SolitaryLeafCollector.build(
        callParameters, task.callParameters, descriptionComputer, notUsedDescription,
        permanentSessionEnvironment, levels.first().additional, mlModel, apiPlatform, levels.first(), mlModel.knownFeatures
      )
      collector.handleCollectedTree(analyseThenLogStructure)
      MLModelPrediction(mlModel, collector)
    }
    else {
      val collector = RootCollector.build(
        callParameters, task.callParameters, descriptionComputer, notUsedDescription,
        permanentSessionEnvironment, levels.first().additional, mlModel, apiPlatform, levels, mlModel.knownFeatures
      )
      collector.handleCollectedTree(analyseThenLogStructure)
      MLModelPredictionBranching(mlModel, collector)
    }

    sessionListener = approachListener.onStartedSession(session)

    return Session.StartOutcome.Success(session)
  }

  override val approachDeclaration: MLTaskApproach.Declaration
    get() {
      approachValidation

      return MLTaskApproach.Declaration(
        sessionFeatures = analysisMethod.sessionAnalysisDeclaration,
        levelsScheme = levels.map { levelTiers ->
          LevelSignature(
            buildMainTiersScheme(levelTiers.main, apiPlatform),
            buildAdditionalTiersScheme(levelTiers.additional, apiPlatform),
          )
        }
      )
    }

  private fun validateApproach() {
    require(task.levels.size == additionallyDescribedTiers.size) {
      "Task $task has ${task.levels.size} levels, when 'additionallyDescribedTiers' has ${additionallyDescribedTiers.size}"
    }

    require(levels.isNotEmpty()) { "Task must declare at least one level" }

    val maybeDuplicatedTaskTiers = levels.flatMap { it.main + it.additional }
    val taskTiers = maybeDuplicatedTaskTiers.toSet()

    require(maybeDuplicatedTaskTiers.size == taskTiers.size) {
      "There are duplicated tiers in the declaration: ${maybeDuplicatedTaskTiers - taskTiers}"
    }
  }

  private fun validateNotUsedDescription(notUsedDescription: PerTier<FeatureFilter>) {
    val maybeDuplicatedTaskTiers = levels.flatMap { it.main + it.additional }
    val taskTiers = maybeDuplicatedTaskTiers.toSet()
    require(notUsedDescription.keys == taskTiers) {
      "Selectors for those and only those tiers must be represented in the 'notUsedDescription' that are present in the task. " +
      "Missing: ${taskTiers - notUsedDescription.keys}, " +
      "Redundant: ${notUsedDescription.keys - taskTiers}"
    }
  }

  private fun buildTierDescriptionDeclaration(tierDescriptors: Collection<TierDescriptor>): Set<FeatureDeclaration<*>> {
    return tierDescriptors.flatMap {
      if (it is ObsoleteTierDescriptor) it.partialDescriptionDeclaration else it.descriptionDeclaration
    }.toSet()
  }

  private fun buildMainTiersScheme(tiers: Set<Tier<*>>, apiEnvironment: MLApiPlatform): PerTier<MainTierScheme> {
    val tiersDescriptors = apiEnvironment.getDescriptorsOfTiers(tiers)

    return tiers.associateWith { tier ->
      val tierDescriptors = tiersDescriptors.getValue(tier)
      val descriptionDeclaration = buildTierDescriptionDeclaration(tierDescriptors)
      val analysisDeclaration = analysisMethod.structureAnalysisDeclaration[tier] ?: emptySet()
      MainTierScheme(descriptionDeclaration, analysisDeclaration)
    }
  }

  private fun buildAdditionalTiersScheme(tiers: Set<Tier<*>>, apiEnvironment: MLApiPlatform): PerTier<AdditionalTierScheme> {
    val tiersDescriptors = apiEnvironment.getDescriptorsOfTiers(tiers)

    return tiers.associateWith { tier ->
      val tierDescriptors = tiersDescriptors.getValue(tier)
      val descriptionDeclaration = buildTierDescriptionDeclaration(tierDescriptors)
      AdditionalTierScheme(descriptionDeclaration)
    }
  }
}

/**
 * An exception that indicates that for some reason, it was not possible to provide an ML model
 * when calling [com.intellij.platform.ml.impl.model.MLModel.Provider.provideModel].
 * The session's start is considered as failed.
 */
@ApiStatus.Internal
class ModelNotAcquiredOutcome<P : Any> : Session.StartOutcome.Failure<P>() {
  override val failureDetails: String = "ML Model was not provided"
}
