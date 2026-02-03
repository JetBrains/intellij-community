// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.environment.Environment
import org.jetbrains.annotations.ApiStatus

/**
 * A period of making predictions with an ML model.
 *
 * The session's depth corresponds to the number of levels in a [com.intellij.platform.ml.MLTask].
 *
 * This is a builder of the tree-like session structure.
 * To learn how sessions work, please address this interface's implementations.
 */
@ApiStatus.Internal
sealed interface Session<P : Any> {
  /**
   * An outcome of an ML session's start.
   *
   * It is considered to be a non-exceptional situation when it is not possible to start an ML session.
   * If so, then a [Failure] is returned and could be handled by the user.
   */
  sealed interface StartOutcome<P : Any> {
    /**
     * A ready-to-use ml session, in case the start was successful
     */
    val session: Session<P>?

    /**
     * Indicates that nothing went wrong, and the start was successful
     */
    class Success<P : Any>(override val session: Session<P>) : StartOutcome<P>

    /**
     * Indicates that there was some issue during the start.
     * The problem could be identified more precisely by looking at
     * the [Failure]'s class.
     */
    open class Failure<P : Any> : StartOutcome<P> {
      override val session: Session<P>? = null

      open val failureDetails: String
        get() = "Unable to start ml session, failure: $this"

      open fun asThrowable(): Throwable {
        return Exception(failureDetails)
      }
    }

    class UncaughtException<P : Any>(val exception: Throwable) : Failure<P>() {
      override fun asThrowable(): Throwable {
        return exception
      }
    }
  }
}

/**
 * A session, that holds other sessions.
 */
@ApiStatus.Internal
interface NestableMLSession<P : Any> : Session<P> {
  /**
   * Start another nested session within this one, that will inherit
   * this session's features.
   *
   * @param callParameters The parameters passed by the MLTask's user
   * @param levelMainEnvironment The main session's environment that contains main ML task's tiers.
   *
   * @return Either [NestableMLSession] or [SinglePrediction], depending on whether the
   * last ML task's level has been reached.
   */
  suspend fun createNestedSession(callParameters: Environment, levelMainEnvironment: Environment): Session<P>

  /**
   * Declare that no more nested sessions will be created from this moment on.
   * It must be called.
   */
  suspend fun onLastNestedSessionCreated()
}

/**
 * A session, that is dedicated to create one prediction at most.
 */
@ApiStatus.Internal
interface SinglePrediction<P : Any> : Session<P> {
  /**
   * Call ML model's inference and produce the prediction.
   * On one object, exactly one function must be called during its lifetime: this or [cancelPrediction]
   */
  suspend fun predict(): P

  /**
   * Declare that model's inference will not be called.
   * It must be called in case it's decided that a prediction is not needed.
   */
  suspend fun cancelPrediction()
}
