// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import com.intellij.platform.ml.impl.session.DescribedSessionTree
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * An analyzer that gives analytical features to the session tree's nodes.
 *
 * For example, if you want to give some analysis to the session tree's root, then you will return
 * mapOf(sessionTreeRoot to setOf(...)).
 *
 * @see com.intellij.platform.ml.impl.session.SessionTree.Visitor to learn how you could walk the tree's nodes
 * @see com.intellij.platform.ml.impl.session.SessionTree.LevelVisitor to see learn how you could session's levels.
 */
@ApiStatus.Internal
interface StructureAnalyser<M, P> {
  /**
   * Contains all features' declarations - [com.intellij.platform.ml.FeatureDeclaration],
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

  fun startSessionAnalysis(callParameters: Environment, sessionEnvironment: Environment) : SessionAnalyser<M, P>
}

@ApiStatus.Internal
interface SessionAnalyser<M : MLModel<P>, P : Any> {
  /**
   * Called before we have attempted to start session and only have initial environment
   */
  suspend fun onBeforeSessionStarted(): List<EventPair<*>>

  /**
   * Called when we've failed to start session, or an exception has been thrown during runtime
   */
  suspend fun onSessionFailedToStart(failure: Session.StartOutcome.Failure<P>): List<EventPair<*>>

  /**
   * Called when we've failed to start session, or an exception has been thrown during runtime
   */
  suspend fun onSessionFailedWithException(exception: Throwable): List<EventPair<*>>

  /**
   * Called when an ML model has been successfully acquired, and we were able to start the ML session
   */
  suspend fun onSessionStarted(session: Session<P>, mlModel: M): List<EventPair<*>>

  /**
   * Called when the session has been successfully finished
   */
  suspend fun onSessionFinished(sessionTreeRoot: DescribedRootContainer<M, P>): List<EventPair<*>>

  abstract class Default<M : MLModel<P>, P : Any> : SessionAnalyserProvider<M, P>, SessionAnalyser<M, P> {
    protected lateinit var callParameters: Environment
    protected lateinit var sessionEnvironment: Environment

    override fun startSessionAnalysis(callParameters: Environment, sessionEnvironment: Environment): SessionAnalyser<M, P> {
      this.callParameters = callParameters
      this.sessionEnvironment = sessionEnvironment
      return this
    }

    override suspend fun onBeforeSessionStarted(): List<EventPair<*>> = emptyList()

    override suspend fun onSessionFailedToStart(failure: Session.StartOutcome.Failure<P>): List<EventPair<*>> = emptyList()

    override suspend fun onSessionFailedWithException(exception: Throwable): List<EventPair<*>> = emptyList()

    override suspend fun onSessionStarted(session: Session<P>, mlModel: M): List<EventPair<*>> = emptyList()

    override suspend fun onSessionFinished(sessionTreeRoot: DescribedRootContainer<M, P>): List<EventPair<*>> = emptyList()
  }
}

@ApiStatus.Internal
internal fun <M : MLModel<P>, P : Any> Collection<SessionAnalyserProvider<M, P>>.createJoinedAnalyser() = object : SessionAnalyserProvider<M, P> {
  val analyserProviders = this@createJoinedAnalyser

  override val declaration: List<EventField<*>> = analyserProviders.flatMap { it.declaration }

  override fun startSessionAnalysis(callParameters: Environment, sessionEnvironment: Environment): SessionAnalyser<M, P> {
    val analysers = analyserProviders.map { it.startSessionAnalysis(callParameters, sessionEnvironment) }
    return object : SessionAnalyser<M, P> {
      override suspend fun onBeforeSessionStarted(): List<EventPair<*>> {
        return analysers.flatMap { it.onBeforeSessionStarted() }
      }

      override suspend fun onSessionFailedToStart(failure: Session.StartOutcome.Failure<P>): List<EventPair<*>> {
        return analysers.flatMap { it.onSessionFailedToStart(failure) }
      }

      override suspend fun onSessionFailedWithException(exception: Throwable): List<EventPair<*>> {
        return analysers.flatMap { it.onSessionFailedWithException(exception) }
      }

      override suspend fun onSessionStarted(session: Session<P>, mlModel: M): List<EventPair<*>> {
        return analysers.flatMap { it.onSessionStarted(session, mlModel) }
      }

      override suspend fun onSessionFinished(sessionTreeRoot: DescribedRootContainer<M, P>): List<EventPair<*>> {
        return analysers.flatMap { it.onSessionFinished(sessionTreeRoot) }
      }
    }
  }
}

@ApiStatus.Internal
internal fun <M : MLModel<P>, P : Any> Collection<StructureAnalyser<M, P>>.createJoinedAnalyser() = object : StructureAnalyser<M, P> {
  val analysers = this@createJoinedAnalyser

  override val declaration: PerTier<Set<FeatureDeclaration<*>>> = analysers.map { it.declaration }.mergePerTier { mutableSetOf() }

  override suspend fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): Map<DescribedSessionTree<M, P>, PerTier<Set<Feature>>> {
    return analysers
      .map { analyser -> analyser.analyse(sessionTreeRoot) }
      .flatMap { it.entries }
      .groupBy({ it.key }, { it.value })
      .mapValues { it.value.mergePerTier { mutableSetOf() } }
  }
}
