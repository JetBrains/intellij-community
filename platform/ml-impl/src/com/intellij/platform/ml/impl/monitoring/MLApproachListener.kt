// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.monitoring

import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.MLTaskApproachBuilder
import com.intellij.platform.ml.impl.apiPlatform.MLTaskGroupListenerProvider
import com.intellij.platform.ml.impl.monitoring.MLApproachInitializationListener.Companion.asJoinedListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener.Companion.asJoinedListener
import com.intellij.platform.ml.impl.monitoring.MLSessionListener.Companion.asJoinedListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus

/**
 * Provides listeners for a set of [com.intellij.platform.ml.impl.MLTaskApproach]
 *
 * Only [com.intellij.platform.ml.impl.LogDrivenModelInference] and the subclasses, that are
 * calling [com.intellij.platform.ml.impl.LogDrivenModelInference.startSession] are monitored.
 */
@ApiStatus.Internal
interface MLTaskGroupListener {
  /**
   * For every approach, the [MLTaskGroupListener] is interested in this value provides a collection of
   * [MLApproachInitializationListener]
   *
   * The comfortable way to create this accordance would be by using
   * [com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy] infix function.
   */
  val approachListeners: Collection<ApproachListeners<*, *>>

  /**
   * A type-safe pair of approach's class and a set of listeners
   *
   * A proper way to create it is to use [monitoredBy]
   */
  data class ApproachListeners<R, P : Any> internal constructor(
    val taskApproachBuilder: Class<out MLTaskApproachBuilder<P>>,
    val approachListener: Collection<MLApproachInitializationListener<R, P>>
  ) {
    companion object {
      infix fun <R, P : Any> Class<out MLTaskApproachBuilder<P>>.monitoredBy(approachListener: MLApproachInitializationListener<R, P>) = ApproachListeners(
        this, listOf(approachListener))

      infix fun <R, P : Any> Class<out MLTaskApproachBuilder<P>>.monitoredBy(approachListeners: Collection<MLApproachInitializationListener<R, P>>) = ApproachListeners(
        this, approachListeners)
    }
  }

  @ApiStatus.Internal
  interface Default : MLTaskGroupListener, MLTaskGroupListenerProvider {
    override fun provide(collector: (MLTaskGroupListener) -> Unit) {
      return collector(this)
    }
  }

  companion object {
    internal val MLTaskGroupListener.targetedApproaches: Set<Class<out MLTaskApproachBuilder<*>>>
      get() = approachListeners.map { it.taskApproachBuilder }.toSet()

    internal fun <P : Any, R> MLTaskGroupListener.onAttemptedToStartSession(taskApproachBuilder: MLTaskApproachBuilder<P>,
                                                                            permanentSessionEnvironment: Environment): MLApproachListener<R, P>? {
      @Suppress("UNCHECKED_CAST")
      val approachListeners: List<MLApproachInitializationListener<R, P>> = approachListeners
        .filter { it.taskApproachBuilder == taskApproachBuilder.javaClass }
        .flatMap { it.approachListener } as List<MLApproachInitializationListener<R, P>>
      return approachListeners.asJoinedListener().onAttemptedToStartSession(permanentSessionEnvironment)
    }
  }
}

/**
 * Listens to the attempt of starting new [Session] of the [MLTaskApproach], that this listener was put
 * into correspondence to via [com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.ApproachListeners.Companion.monitoredBy]
 *
 * @param R Type of the [com.intellij.platform.ml.impl.model.MLModel]
 * @param P Prediction's type
 */
@ApiStatus.Internal
fun interface MLApproachInitializationListener<R, P : Any> {
  /**
   * Called each time, when [com.intellij.platform.ml.impl.LogDrivenModelInference.startSession] is invoked
   *
   * @return A listener, that will be monitoring how successful the start was. If it is not needed, null is returned.
   */
  fun onAttemptedToStartSession(permanentSessionEnvironment: Environment): MLApproachListener<R, P>?

  companion object {
    fun <R, P : Any> Collection<MLApproachInitializationListener<R, P>>.asJoinedListener(): MLApproachInitializationListener<R, P> =
      MLApproachInitializationListener { permanentSessionEnvironment ->
        val approachListeners = this@asJoinedListener.mapNotNull { it.onAttemptedToStartSession(permanentSessionEnvironment) }
        if (approachListeners.isEmpty()) null else approachListeners.asJoinedListener()
      }
  }
}

/**
 * Listens to the process of starting new [Session] of [com.intellij.platform.ml.impl.LogDrivenModelInference].
 */
@ApiStatus.Internal
interface MLApproachListener<R, P : Any> {
  /**
   * Called if the session was not started,
   * on exceptionally rare occasions,
   * when the [com.intellij.platform.ml.impl.LogDrivenModelInference.startSession] failed with an exception
   */
  fun onFailedToStartSessionWithException(exception: Throwable) {}

  /**
   * Called if the session was not started,
   * but the failure is 'ordinary'.
   */
  fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) {}

  /**
   * Called when a new [com.intellij.platform.ml.impl.LogDrivenModelInference]'s session was started successfully.
   *
   * @return A listener for tracking the session's progress, null if the session will not be tracked.
   */
  fun onStartedSession(session: Session<P>): MLSessionListener<R, P>?

  companion object {
    fun <R, P : Any> Collection<MLApproachListener<R, P>>.asJoinedListener(): MLApproachListener<R, P> {
      val approachListeners = this@asJoinedListener

      return object : MLApproachListener<R, P> {
        override fun onFailedToStartSessionWithException(exception: Throwable) =
          approachListeners.forEach { it.onFailedToStartSessionWithException(exception) }

        override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) = approachListeners.forEach {
          it.onFailedToStartSession(failure)
        }

        override fun onStartedSession(session: Session<P>): MLSessionListener<R, P>? {
          val listeners = approachListeners.mapNotNull { it.onStartedSession(session) }
          return if (listeners.isEmpty()) null else listeners.asJoinedListener()
        }
      }
    }
  }
}

/**
 * Listens to session events of a [com.intellij.platform.ml.impl.LogDrivenModelInference]
 */
@ApiStatus.Internal
interface MLSessionListener<R, P : Any> {
  /**
   * All tier instances were established (the tree will not be growing further),
   * described, and predictions in the [sessionTree] were finished.
   */
  fun onSessionDescriptionFinished(sessionTree: DescribedRootContainer<R, P>) {}

  /**
   * Called only after [onSessionDescriptionFinished]
   *
   * All tree nodes were analyzed.
   */
  fun onSessionAnalysisFinished(sessionTree: AnalysedRootContainer<P>) {}

  companion object {
    fun <R, P : Any> Collection<MLSessionListener<R, P>>.asJoinedListener(): MLSessionListener<R, P> {
      val sessionListeners = this@asJoinedListener

      return object : MLSessionListener<R, P> {
        override fun onSessionDescriptionFinished(sessionTree: DescribedRootContainer<R, P>) = sessionListeners.forEach {
          it.onSessionDescriptionFinished(sessionTree)
        }

        override fun onSessionAnalysisFinished(sessionTree: AnalysedRootContainer<P>) = sessionListeners.forEach {
          it.onSessionAnalysisFinished(sessionTree)
        }
      }
    }
  }
}
