// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.session

import com.intellij.platform.ml.NestableMLSession
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.Session.StartOutcome
import com.intellij.platform.ml.Session.StartOutcome.Failure
import com.intellij.platform.ml.Session.StartOutcome.Success
import com.intellij.platform.ml.SinglePrediction
import com.intellij.platform.ml.environment.Environment
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <P : Any> StartOutcome<P>.requireSuccess(): Session<P> = when (this) {
  is Failure -> throw this.asThrowable()
  is Success -> this.session
}

/**
 * A wrapper for convenient usage of a [NestableMLSession].
 *
 * This function assumes that this session is nestable.
 */
@ApiStatus.Internal
suspend fun <P : Any> Session<P>.withNestedSessions(useCreator: suspend (NestableSessionWrapper<P>) -> Unit) {
  val nestableMLSession = requireNotNull(this as? NestableMLSession<P>)

  val creator = object : NestableSessionWrapper<P> {
    override suspend fun nestConsidering(callParameters: Environment, levelEnvironment: Environment): Session<P> {
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
 * A wrapper for convenient usage of a [SinglePrediction].
 *
 * This function assumes that this session is [NestableMLSession], and the nested
 * sessions' types are [SinglePrediction].
 */
@ApiStatus.Internal
suspend fun <T, P : Any> Session<P>.withPredictions(useModelWrapper: suspend (ModelWrapper<P>) -> T): T {
  val nestableMLSession = requireNotNull(this as? NestableMLSession<P>)
  val predictor = object : ModelWrapper<P> {
    override suspend fun predictConsidering(callParameters: Environment, predictionEnvironment: Environment): P {
      val predictionSession = nestableMLSession.createNestedSession(callParameters, predictionEnvironment)
      require(predictionSession is SinglePrediction<P>)
      return predictionSession.predict()
    }

    override suspend fun consider(callParameters: Environment, predictionEnvironment: Environment) {
      val predictionSession = nestableMLSession.createNestedSession(callParameters, predictionEnvironment)
      require(predictionSession is SinglePrediction<P>)
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
interface NestableSessionWrapper<P : Any> {
  suspend fun nestConsidering(callParameters: Environment, levelEnvironment: Environment): Session<P>
}

@ApiStatus.Internal
interface ModelWrapper<P : Any> {
  suspend fun predictConsidering(callParameters: Environment, predictionEnvironment: Environment): P

  suspend fun consider(callParameters: Environment, predictionEnvironment: Environment)
}
