// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.analysis

import com.intellij.platform.ml.*
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureDeclaration
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
import com.intellij.platform.ml.session.DescribedRootContainer
import com.intellij.platform.ml.session.DescribedSessionTree
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * An analyzer that gives analytical features to the session tree's nodes.
 *
 * For example, if you want to give some analysis to the session tree's root, then you will return
 * mapOf(sessionTreeRoot to setOf(...)).
 *
 * @see com.intellij.platform.ml.session.SessionTree.Visitor to learn how you could walk the tree's nodes
 * @see com.intellij.platform.ml.session.SessionTree.LevelVisitor to see learn how you could session's levels.
 */
@ApiStatus.Internal
interface StructureAnalyser<M, P> {
  /**
   * Contains all features' declarations - [com.intellij.platform.ml.feature.FeatureDeclaration],
   * that are then used in the analysis as [analyse]'s return value.
   */
  val declaration: PerTier<Set<FeatureDeclaration<*>>>

  /**
   * Performs session tree's analysis.
   * The analysis is performed asynchronously,
   * so the function is not required to return the final result, but only a [CompletableFuture]
   * that will be fulfilled when the analysis is finished.
   */
  suspend fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): Map<DescribedSessionTree<M, P>, PerTier<Set<Feature>>>
}

@ApiStatus.Internal
interface SessionAnalyserProvider<M : MLModel<P>, P : Any> {
  val declaration: List<EventField<*>>

  /**
   * @param callParameters The parameters that were passed from the MLTask usage place.
   * @param sessionEnvironment The environment, that is described to be passed to the ML model.
   *
   * @return The analyzer, that will be contributing to the ML logs. Returns null, if none analysis should be run now.
   */
  fun startSessionAnalysis(callParameters: Environment, sessionEnvironment: Environment) : SessionAnalyser<M, P>?
}

@ApiStatus.Internal
interface SessionAnalyser<M : MLModel<P>, P : Any> {
  /**
   * Called before we have attempted to start session and only have initial environment
   */
  suspend fun onBeforeSessionStarted(): List<EventPair<*>> = emptyList()

  /**
   * Called when we've failed to start session, or an exception has been thrown during runtime
   */
  suspend fun onSessionFailedToStart(failure: Session.StartOutcome.Failure<P>): List<EventPair<*>> = emptyList()

  /**
   * Called when we've failed to start session, or an exception has been thrown during runtime
   */
  suspend fun onSessionFailedWithException(exception: Throwable): List<EventPair<*>> = emptyList()

  /**
   * Called when an ML model has been successfully acquired, and we were able to start the ML session
   */
  suspend fun onSessionStarted(session: Session<P>, mlModel: M): List<EventPair<*>> = emptyList()

  /**
   * Called when the session has been successfully finished
   */
  suspend fun onSessionFinished(sessionTreeRoot: DescribedRootContainer<M, P>): List<EventPair<*>> = emptyList()

  abstract class Default<M : MLModel<P>, P : Any> : SessionAnalyserProvider<M, P> {
    private inner class EnvironmentAwareAnalyser(
      private val callParameters: Environment,
      private val sessionEnvironment: Environment,
    ) : SessionAnalyser<M, P> {
      override suspend fun onBeforeSessionStarted() = this@Default.onBeforeSessionStarted(callParameters, sessionEnvironment)

      override suspend fun onSessionFailedToStart(failure: Session.StartOutcome.Failure<P>) = this@Default.onSessionFailedToStart(callParameters, sessionEnvironment, failure)

      override suspend fun onSessionFailedWithException(exception: Throwable) = this@Default.onSessionFailedWithException(callParameters, sessionEnvironment, exception)

      override suspend fun onSessionStarted(session: Session<P>, mlModel: M) = this@Default.onSessionStarted(callParameters, sessionEnvironment, session, mlModel)

      override suspend fun onSessionFinished(sessionTreeRoot: DescribedRootContainer<M, P>) = this@Default.onSessionFinished(callParameters, sessionEnvironment, sessionTreeRoot)
    }

    open suspend fun onBeforeSessionStarted(callParameters: Environment, sessionEnvironment: Environment): List<EventPair<*>> = emptyList()

    open suspend fun onSessionFailedToStart(callParameters: Environment, sessionEnvironment: Environment, failure: Session.StartOutcome.Failure<P>): List<EventPair<*>> = emptyList()

    open suspend fun onSessionFailedWithException(callParameters: Environment, sessionEnvironment: Environment, exception: Throwable): List<EventPair<*>> = emptyList()

    open suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<P>, mlModel: M): List<EventPair<*>> = emptyList()

    open suspend fun onSessionFinished(callParameters: Environment, sessionEnvironment: Environment, sessionTreeRoot: DescribedRootContainer<M, P>): List<EventPair<*>> = emptyList()

    override fun startSessionAnalysis(callParameters: Environment, sessionEnvironment: Environment): SessionAnalyser<M, P> {
      return EnvironmentAwareAnalyser(callParameters, sessionEnvironment)
    }
  }
}

internal fun <M : MLModel<P>, P : Any> Collection<SessionAnalyserProvider<M, P>>.createJoinedAnalyser(systemLoggerBuilder: SystemLoggerBuilder) = object : SessionAnalyserProvider<M, P> {
  val analyserProviders = this@createJoinedAnalyser

  override val declaration: List<EventField<*>> = analyserProviders.flatMap { it.declaration }

  override fun startSessionAnalysis(callParameters: Environment, sessionEnvironment: Environment): SessionAnalyser<M, P>? {
    val analysers = analyserProviders.mapNotNull { it.startSessionAnalysis(callParameters, sessionEnvironment) }
    if (analysers.isEmpty()) return null

    return object : SessionAnalyser<M, P> {
      override suspend fun onBeforeSessionStarted(): List<EventPair<*>> {
        return analyseAll(analysers.map { { it.onBeforeSessionStarted().alsoDebug("before started", it) } })
      }

      override suspend fun onSessionFailedToStart(failure: Session.StartOutcome.Failure<P>): List<EventPair<*>> {
        return analyseAll(analysers.map { { it.onSessionFailedToStart(failure).alsoDebug("failed to start", it) } })
      }

      override suspend fun onSessionFailedWithException(exception: Throwable): List<EventPair<*>> {
        return analyseAll(analysers.map { { it.onSessionFailedWithException(exception).alsoDebug("failed with exception", it) } })
      }

      override suspend fun onSessionStarted(session: Session<P>, mlModel: M): List<EventPair<*>> {
        return analyseAll(analysers.map { { it.onSessionStarted(session, mlModel).alsoDebug("session started", it) } })
      }

      override suspend fun onSessionFinished(sessionTreeRoot: DescribedRootContainer<M, P>): List<EventPair<*>> {
        return analyseAll(analysers.map { { it.onSessionFinished(sessionTreeRoot).alsoDebug("session finished", it) } })
      }

      private suspend fun analyseAll(analysers: Collection<suspend () -> List<EventPair<*>>>): List<EventPair<*>> = coroutineScope {
        val analysisJobs = analysers.map { async { it() } }
        return@coroutineScope analysisJobs.flatMap { it.await() }
      }

      private fun List<EventPair<*>>.alsoDebug(event: String, analyser: SessionAnalyser<M, P>): List<EventPair<*>> {
        systemLoggerBuilder.build(SessionAnalyser::class.java).debug {
          "Analyser ${analyser.javaClass} produced the following fields on $event: ${this.map { it.field.name }}"
        }
        return this
      }
    }
  }
}

internal fun <M : MLModel<P>, P : Any> Collection<StructureAnalyser<M, P>>.createJoinedAnalyser() = object : StructureAnalyser<M, P> {
  val analysers = this@createJoinedAnalyser

  override val declaration: PerTier<Set<FeatureDeclaration<*>>> = analysers.map { it.declaration }.mergePerTier { mutableSetOf() }

  override suspend fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): Map<DescribedSessionTree<M, P>, PerTier<Set<Feature>>> = coroutineScope {
    analysers
      .map { async { it.analyse(sessionTreeRoot) } }
      .map { it.await() }
      .flatMap { it.entries }
      .groupBy({ it.key }, { it.value })
      .mapValues { it.value.mergePerTier { mutableSetOf() } }
  }
}
