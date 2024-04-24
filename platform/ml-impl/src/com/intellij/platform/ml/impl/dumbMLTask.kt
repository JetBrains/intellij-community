// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.NestableSessionWrapper
import com.intellij.platform.ml.impl.session.analysis.SessionAnalyser
import org.jetbrains.annotations.ApiStatus

/**
 * Marker for classes and interfaces from the ML API, that are working within the dumb predictions mode: when we don't have an ML Model.
 */
@ApiStatus.Internal
interface DumbPredictionsMode

typealias DumbSession = Session<Unit>

/**
 * If you don't have an ML model yet, use this base class to declare your MLTask.
 */
@ApiStatus.Internal
open class DumbMLTask(
  name: String, levels: List<Set<Tier<*>>>, callParameters: List<Set<Tier<*>>>
) : MLTask<Unit>(name, levels, callParameters, Unit::class.java), DumbPredictionsMode

/**
 * Represents a not yet trained model, or a model, whose predictions can't be relied on
 */
@ApiStatus.Internal
class DumbMLModel : MLModel<Unit>, DumbPredictionsMode {
  override val knownFeatures: PerTier<FeatureSelector> = emptyMap()

  override fun predict(callParameters: List<Environment>, features: PerTier<Set<Feature>>) = Unit

  object Provider : MLModel.Provider<DumbMLModel, Unit>, DumbPredictionsMode {
    override fun provideModel(callParameters: Environment, environment: Environment, sessionTiers: List<LevelTiers>): DumbMLModel {
      return DumbMLModel()
    }
  }
}

/**
 * TODO
 */
@ApiStatus.Internal
interface DumbSessionDetails : LogDrivenModelInference.SessionDetails<DumbMLModel, Unit>, DumbPredictionsMode {
  override val mlModelProvider
    get() = DumbMLModel.Provider

  open class Default(mlTask: MLTask<Unit>) : DumbSessionDetails, LogDrivenModelInference.SessionDetails.Default<DumbMLModel, Unit>(mlTask)
}

/**
 * Register this approach builder as an extension point [com.intellij.platform.ml.impl.MLTaskApproachBuilder.Companion.EP_NAME]
 * to avoid providing an ML Model to your approach
 */
@ApiStatus.Internal
open class DumbApproachBuilder(task: MLTask<Unit>) : LogDrivenModelInference.Builder<DumbMLModel, Unit>(task, { DumbSessionDetails.Default(task) }), DumbPredictionsMode

/**
 * A wrapper for convenient usage of a [NestableMLSession], when we don't have yet a trained model
 *
 * This function assumes that this session is nestable.
 */
@ApiStatus.Internal
suspend fun DumbSession.withNestedDumbSessions(useCreator: suspend (NestableSessionWrapper<Unit>) -> Unit) {
  val nestableMLSession = requireNotNull(this as? NestableMLSession)

  val creator = object : NestableSessionWrapper<Unit> {
    override suspend fun nestConsidering(callParameters: Environment, levelEnvironment: Environment): DumbSession {
      return nestableMLSession.createNestedSession(callParameters, levelEnvironment)
    }
  }

  try {
    return useCreator(creator)
  }
  finally {
    nestableMLSession.onLastNestedSessionCreated()
  }
}

/**
 * A wrapper for convenient usage of a [SinglePrediction] in case of a dumb session, when the ml model is not trained,
 * hence it can't perform predictions.
 *
 * This function assumes that this session is [NestableMLSession], and the nested
 * sessions' types are [SinglePrediction].
 */
@ApiStatus.Internal
suspend fun <T> DumbSession.withConsiderations(useModelWrapper: suspend (DumbModelWrapper) -> T): T {
  val nestableMLSession = requireNotNull(this as? NestableMLSession)
  val predictor = object : DumbModelWrapper {
    override suspend fun consider(callParameters: Environment, predictionEnvironment: Environment) {
      val predictionSession = nestableMLSession.createNestedSession(callParameters, predictionEnvironment)
      require(predictionSession is SinglePrediction<Unit>)
      predictionSession.cancelPrediction()
    }
  }
  try {
    return useModelWrapper(predictor)
  }
  finally {
    nestableMLSession.onLastNestedSessionCreated()
  }
}

@ApiStatus.Internal
interface DumbModelWrapper : DumbPredictionsMode {
  suspend fun consider(callParameters: Environment, predictionEnvironment: Environment)
}

@ApiStatus.Internal
class DumbModeAnalyser<M : MLModel<P>, P : Any> : SessionAnalyser.Default<M, P>(), DumbPredictionsMode {
  companion object {
    val IS_DUMB_MODEL = BooleanEventField("is_dumb_model")
  }

  override suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<P>, mlModel: M): List<EventPair<*>> {
    return listOf<EventPair<*>>(
      IS_DUMB_MODEL with (mlModel is DumbPredictionsMode)
    )
  }

  override val declaration: List<EventField<*>> = listOf(IS_DUMB_MODEL)
}
