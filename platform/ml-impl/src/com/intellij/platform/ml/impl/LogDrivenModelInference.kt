// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ml.*
import com.intellij.platform.ml.ScopeEnvironment.Companion.narrowedTo
import com.intellij.platform.ml.impl.LogDrivenModelInference.SessionDetails.Companion.getLevels
import com.intellij.platform.ml.impl.LogDrivenModelInference.SessionDetails.Companion.validate
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.getDescriptorsOfTiers
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.Companion.getJoinedListenerForTask
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener
import com.intellij.platform.ml.impl.session.*
import com.intellij.platform.ml.impl.session.analysis.MLModelAnalyser
import com.intellij.platform.ml.impl.session.analysis.MLSessionAnalyser
import com.intellij.platform.ml.impl.session.analysis.StructureAnalyser
import org.jetbrains.annotations.ApiStatus

/**
 * The main way to apply classical machine learning approaches: run the ML model, collect the logs, retrain the model, repeat.
 *
 * @param task The task that is solved by this approach.
 * @param apiPlatform The platform, that the approach will be running within.
 * @param sessionDetails Preferences on how the session will be executed
 * @param thisBuilder The builder that has created this class.
 * It is essential to put it, otherwise [com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener] will not manage to find the
 * correct task listener.
 */
@ApiStatus.Internal
open class LogDrivenModelInference<M : MLModel<P>, P : Any>(
  final override val task: MLTask<P>,
  final override val apiPlatform: MLApiPlatform,
  private val sessionDetails: SessionDetails<M, P>,
  private val thisBuilder: MLTaskApproachBuilder<P>,
) : MLTaskApproach<P> {
  private val sessionLevels = sessionDetails.getLevels(task)

  interface SessionDetails<M : MLModel<P>, P : Any> {
    fun interface Builder<M : MLModel<P>, P : Any> {
      fun build(apiPlatform: MLApiPlatform): SessionDetails<M, P>
    }

    /**
     * The analyzers, that are analyzing session's tree-like structure, giving additional features,
     * not used by the ML model, to the nodes.
     */
    val structureAnalysers: Collection<StructureAnalyser<M, P>>

    /**
     * The analyzers, that are giving features to the model, that was used during the session.
     */
    val mlModelAnalysers: Collection<MLModelAnalyser<M, P>>

    /**
     * Provides an ML model to use during session's lifetime.
     */
    val mlModelProvider: MLModel.Provider<M, P>

    /**
     * Performs description's computation.
     * Could perform caching mechanisms to avoid recomputing features every time.
     */
    val descriptionComputer: DescriptionComputer

    /**
     * Tiers that do not make a part of te [task], but they could be described and passed to the ML model.
     *
     * The size of this list must correspond to the number of levels in the solved [task].
     */
    val additionallyDescribedTiers: List<Set<Tier<*>>>

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
    fun getNotUsedDescription(callParameters: Environment, mlModel: M): PerTier<FeatureFilter>

    companion object {
      fun <M : MLModel<P>, P : Any> SessionDetails<M, P>.getLevels(task: MLTask<P>): List<LevelSignature<Set<Tier<*>>, Set<Tier<*>>>> {
        return (task.levels zip additionallyDescribedTiers).map { LevelSignature(it.first, it.second) }
      }

      fun <M : MLModel<P>, P : Any> SessionDetails<M, P>.validate(task: MLTask<P>) {
        require(task.levels.size == additionallyDescribedTiers.size) {
          "Task $task has ${task.levels.size} levels, when 'additionallyDescribedTiers' has ${additionallyDescribedTiers.size}"
        }

        val maybeDuplicatedTaskTiers: List<Tier<*>> = getLevels(task).flatMap { it.main.toList() + it.additional }
        val taskTiers: Set<Tier<*>> = maybeDuplicatedTaskTiers.toSet()

        require(maybeDuplicatedTaskTiers.size == taskTiers.size) {
          "There are duplicated tiers in the declaration: ${maybeDuplicatedTaskTiers.groupBy { it }.filter { it.value.size > 1 }.keys}"
        }
      }
    }
  }

  open class Builder<M : MLModel<P>, P : Any>(
    override val task: MLTask<P>,
    private val details: SessionDetails.Builder<M, P>,
  ) : MLTaskApproachBuilder<P> {
    final override fun buildApproach(apiPlatform: MLApiPlatform): MLTaskApproach<P> {
      val sessionDetails = details.build(apiPlatform)
      sessionDetails.validate(task)
      val modelInference = buildModelInference(apiPlatform, sessionDetails)
      return modelInference
    }

    open fun buildModelInference(apiPlatform: MLApiPlatform, sessionDetails: SessionDetails<M, P>): LogDrivenModelInference<M, P> {
      return LogDrivenModelInference(task, apiPlatform, sessionDetails, this)
    }

    override fun buildApproachSessionDeclaration(apiPlatform: MLApiPlatform): MLTaskApproach.SessionDeclaration {
      val sessionDetails = details.build(apiPlatform)
      sessionDetails.validate(task)
      val sessionAnalyser = MLSessionAnalyser(sessionDetails.structureAnalysers, sessionDetails.mlModelAnalysers)
      val levels = (task.levels zip sessionDetails.additionallyDescribedTiers).map { LevelSignature(it.first, it.second) }
      return MLTaskApproach.SessionDeclaration(
        sessionFeatures = sessionAnalyser.sessionAnalysisDeclaration,
        levelsScheme = levels.map { levelTiers ->
          LevelSignature(
            buildMainTiersScheme(sessionAnalyser, levelTiers.main, apiPlatform),
            buildAdditionalTiersScheme(levelTiers.additional, apiPlatform),
          )
        }
      )
    }

    private fun buildTierDescriptionDeclaration(tierDescriptors: Collection<TierDescriptor>): Set<FeatureDeclaration<*>> {
      return tierDescriptors.flatMap {
        if (it is ObsoleteTierDescriptor) it.partialDescriptionDeclaration else it.descriptionDeclaration
      }.toSet()
    }

    private fun buildMainTiersScheme(sessionAnalyser: MLSessionAnalyser<M, P>, tiers: Set<Tier<*>>, apiEnvironment: MLApiPlatform): PerTier<MainTierScheme> {
      val tiersDescriptors = apiEnvironment.getDescriptorsOfTiers(tiers)

      return tiers.associateWith { tier ->
        val tierDescriptors = tiersDescriptors.getValue(tier)
        val descriptionDeclaration = buildTierDescriptionDeclaration(tierDescriptors)
        val analysisDeclaration = sessionAnalyser.structureAnalysisDeclaration[tier] ?: emptySet()
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

  override suspend fun startSession(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
    return startSessionMonitoring(callParameters, permanentSessionEnvironment)
  }

  private suspend fun startSessionMonitoring(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
    val approachListener = apiPlatform.getJoinedListenerForTask<M, P>(thisBuilder, permanentSessionEnvironment)
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
    val callParameters = unsafeCallParameters.narrowedTo(task.callParameters.first())
    val extendedPermanentSessionEnvironment = Environment.joined(callParameters, permanentSessionEnvironment)

    val mlModel: M = run {
      val nullableMlModel = sessionDetails.mlModelProvider.provideModel(callParameters, extendedPermanentSessionEnvironment, sessionLevels)
      if (nullableMlModel == null) {
        val failure = ModelNotAcquiredOutcome<P>()
        approachListener.onFailedToStartSession(failure)
        return failure
      }
      nullableMlModel
    }

    var sessionListener: MLSessionListener<M, P>? = null

    val sessionAnalyser = MLSessionAnalyser(sessionDetails.structureAnalysers, sessionDetails.mlModelAnalysers)

    val analyseThenLogStructure = SessionTreeHandler<DescribedRootContainer<M, P>, M, P> { treeRoot ->
      sessionListener?.onSessionDescriptionFinished(treeRoot)
      sessionAnalyser.analyseSessionStructure(treeRoot).thenApplyAsync { analysedSession ->
        sessionListener?.onSessionAnalysisFinished(analysedSession)
      }.exceptionally {
        thisLogger().error(it)
      }
    }

    val notUsedDescription = sessionDetails.getNotUsedDescription(callParameters, mlModel)
    validateNotUsedDescription(notUsedDescription)

    val session = if (sessionLevels.size == 1) {
      val collector = SolitaryLeafCollector.build(
        callParameters, task, sessionDetails.descriptionComputer, notUsedDescription,
        permanentSessionEnvironment, sessionLevels.first().additional, mlModel, apiPlatform, sessionLevels.first(), mlModel.knownFeatures,
      )
      collector.handleCollectedTree(analyseThenLogStructure)
      MLModelPrediction(mlModel, collector)
    }
    else {
      val collector = RootCollector.build(
        callParameters, task, sessionDetails.descriptionComputer, notUsedDescription,
        permanentSessionEnvironment, sessionLevels.first().additional, mlModel, apiPlatform, sessionLevels, mlModel.knownFeatures,
      )
      collector.handleCollectedTree(analyseThenLogStructure)
      MLModelPredictionBranching(mlModel, collector)
    }

    sessionListener = approachListener.onStartedSession(session)

    return Session.StartOutcome.Success(session)
  }

  private fun validateNotUsedDescription(notUsedDescription: PerTier<FeatureFilter>) {
    val maybeDuplicatedTaskTiers = sessionLevels.flatMap { it.main + it.additional }
    val taskTiers = maybeDuplicatedTaskTiers.toSet()
    require(notUsedDescription.keys == taskTiers) {
      "Selectors for those and only those tiers must be represented in the 'notUsedDescription' that are present in the task. " +
      "Missing: ${taskTiers - notUsedDescription.keys}, " +
      "Redundant: ${notUsedDescription.keys - taskTiers}"
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
