// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session

import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.NestableMLSession
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.SinglePrediction

/**
 * A wrapper for convenient usage of a [NestableMLSession].
 *
 * This function assumes that this session is nestable.
 */
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

interface NestableSessionWrapper<P : Any> {
  suspend fun nestConsidering(callParameters: Environment, levelEnvironment: Environment): Session<P>
}

interface ModelWrapper<P : Any> {
  suspend fun predictConsidering(callParameters: Environment, predictionEnvironment: Environment): P

  suspend fun consider(callParameters: Environment, predictionEnvironment: Environment)
}
