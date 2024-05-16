// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.monitoring

import com.intellij.platform.ml.*
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.monitoring.MLApproachInitializationListener.Companion.asJoinedListener
import com.intellij.platform.ml.monitoring.MLApproachListener.Companion.asJoinedListener
import com.intellij.platform.ml.monitoring.MLSessionListener.Companion.asJoinedListener
import com.intellij.platform.ml.monitoring.MLTaskGroupListener.ApproachToListener.Companion.monitoredBy
import com.intellij.platform.ml.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus

/**
 * Provides listeners for a set of [com.intellij.platform.ml.MLTaskApproach]
 *
 * Only [com.intellij.platform.ml.LogDrivenModelInference] and the subclasses, that are
 * calling [com.intellij.platform.ml.LogDrivenModelInference.startSession] are monitored.
 */
@ApiStatus.Internal
interface MLTaskGroupListener {
  /**
   * For every approach, the [MLTaskGroupListener] is interested in this value provides a collection of
   * [MLApproachInitializationListener]
   *
   * The comfortable way to create this accordance would be by using
   * [com.intellij.platform.ml.monitoring.MLTaskGroupListener.ApproachToListener.Companion.monitoredBy] infix function.
   */
  val approachListeners: Collection<ApproachToListener<*, *>>

  /**
   * A type-safe pair of approach's class and a set of listeners
   *
   * A proper way to create it is to use [monitoredBy]
   */
  data class ApproachToListener<M : MLModel<P>, P : Any> internal constructor(
    val taskApproachBuilder: Class<out MLTaskApproachBuilder<P>>,
    val approachListener: Collection<MLApproachInitializationListener<M, P>>
  ) {
    companion object {
      infix fun <M : MLModel<P>, P : Any> Class<out MLTaskApproachBuilder<P>>.monitoredBy(approachListener: MLApproachInitializationListener<M, P>) = ApproachToListener(
        this, listOf(approachListener))

      infix fun <M : MLModel<P>, P : Any> Class<out MLTaskApproachBuilder<P>>.monitoredBy(approachListeners: Collection<MLApproachInitializationListener<M, P>>) = ApproachToListener(
        this, approachListeners)
    }
  }

  companion object {
    internal val MLTaskGroupListener.targetedApproaches: Set<Class<out MLTaskApproachBuilder<*>>>
      get() = approachListeners.map { it.taskApproachBuilder }.toSet()

    internal fun <P : Any, M : MLModel<P>> MLTaskGroupListener.onAttemptedToStartSession(taskApproachBuilder: MLTaskApproachBuilder<P>,
                                                                                         apiPlatform: MLApiPlatform,
                                                                                         callEnvironment: Environment,
                                                                                         permanentSessionEnvironment: Environment): MLApproachListener<M, P>? {
      @Suppress("UNCHECKED_CAST")
      val approachListeners: List<MLApproachInitializationListener<M, P>> = approachListeners
        .filter { it.taskApproachBuilder == taskApproachBuilder.javaClass }
        .flatMap { it.approachListener } as List<MLApproachInitializationListener<M, P>>

      return approachListeners.asJoinedListener().onAttemptedToStartSession(apiPlatform, permanentSessionEnvironment, callEnvironment)
    }
  }
}

/**
 * Listens to the attempt of starting new [Session] of the [MLTaskApproach], that this listener was put
 * into correspondence to via [com.intellij.platform.ml.monitoring.MLTaskGroupListener.ApproachToListener.Companion.monitoredBy]
 *
 * @param M Type of the [com.intellij.platform.ml.MLModel]
 * @param P Prediction's type
 */
@ApiStatus.Internal
fun interface MLApproachInitializationListener<M : MLModel<P>, P : Any> {
  /**
   * Called each time, when [com.intellij.platform.ml.LogDrivenModelInference.startSession] is invoked
   *
   * @return A listener, that will be monitoring how successful the start was. If it is not needed, null is returned.
   */
  fun onAttemptedToStartSession(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, permanentCallParameters: Environment): MLApproachListener<M, P>?

  companion object {
    fun <M : MLModel<P>, P : Any> Collection<MLApproachInitializationListener<M, P>>.asJoinedListener(): MLApproachInitializationListener<M, P> =
      MLApproachInitializationListener { apiPlatform, callEnvironment, permanentSessionEnvironment ->
        val approachListeners = this@asJoinedListener.mapNotNull { it.onAttemptedToStartSession(apiPlatform, permanentSessionEnvironment, callEnvironment) }
        if (approachListeners.isEmpty()) null else approachListeners.asJoinedListener()
      }
  }
}

/**
 * Listens to the process of starting new [Session] of [com.intellij.platform.ml.LogDrivenModelInference].
 */
@ApiStatus.Internal
interface MLApproachListener<M : MLModel<P>, P : Any> {
  /**
   * Called if the session was not started,
   * on exceptionally rare occasions,
   * when the [com.intellij.platform.ml.LogDrivenModelInference.startSession] failed with an exception
   *
   * This callback is finishing the session listening: [onStartedSession] won't be called.
   */
  fun onFailedToStartSessionWithException(exception: Throwable) {}

  /**
   * Called if the session was not started,
   * but the failure is 'ordinary'.
   *
   * This callback is finishing the session listening: [onStartedSession] won't be called.
   */
  fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) {}

  /**
   * Called when a new [com.intellij.platform.ml.LogDrivenModelInference]'s session was started successfully.
   *
   * @return A listener for tracking the session's progress, null if the session will not be tracked.
   */
  fun onStartedSession(session: Session<P>, mlModel: M): MLSessionListener<M, P>? = null

  open class Default<M : MLModel<P>, P : Any>(taskApproachBuilder: Class<out MLTaskApproachBuilder<P>>) : MLApproachListener<M, P>, MLTaskGroupListener {
    override val approachListeners: Collection<MLTaskGroupListener.ApproachToListener<*, *>> = listOf(
      taskApproachBuilder monitoredBy MLApproachInitializationListener { _, _, _ ->
        this@Default
      }
    )
  }

  companion object {
    fun <M : MLModel<P>, P : Any> Collection<MLApproachListener<M, P>>.asJoinedListener(): MLApproachListener<M, P> {
      val approachListeners = this@asJoinedListener

      return object : MLApproachListener<M, P> {
        override fun onFailedToStartSessionWithException(exception: Throwable) =
          approachListeners.forEach { it.onFailedToStartSessionWithException(exception) }

        override fun onFailedToStartSession(failure: Session.StartOutcome.Failure<P>) = approachListeners.forEach {
          it.onFailedToStartSession(failure)
        }

        override fun onStartedSession(session: Session<P>, mlModel: M): MLSessionListener<M, P>? {
          val listeners = approachListeners.mapNotNull { it.onStartedSession(session, mlModel) }
          return if (listeners.isEmpty()) null else listeners.asJoinedListener()
        }
      }
    }
  }
}

/**
 * Listens to session events of a [com.intellij.platform.ml.LogDrivenModelInference]
 */
@ApiStatus.Internal
interface MLSessionListener<R, P : Any> {
  /**
   * All tier instances were established (the tree will not be growing further),
   * described, and predictions in the [sessionTree] were finished.
   */
  fun onSessionFinishedSuccessfully(sessionTree: DescribedRootContainer<R, P>) {}

  open class Default<M : MLModel<P>, P : Any>(taskApproachBuilder: Class<out MLTaskApproachBuilder<P>>) : MLSessionListener<M, P>, MLApproachListener.Default<M, P>(taskApproachBuilder) {
    override fun onStartedSession(session: Session<P>, mlModel: M): MLSessionListener<M, P>? {
      return this
    }
  }

  companion object {
    fun <R, P : Any> Collection<MLSessionListener<R, P>>.asJoinedListener(): MLSessionListener<R, P> {
      val sessionListeners = this@asJoinedListener

      return object : MLSessionListener<R, P> {
        override fun onSessionFinishedSuccessfully(sessionTree: DescribedRootContainer<R, P>) = sessionListeners.forEach {
          it.onSessionFinishedSuccessfully(sessionTree)
        }
      }
    }
  }
}
