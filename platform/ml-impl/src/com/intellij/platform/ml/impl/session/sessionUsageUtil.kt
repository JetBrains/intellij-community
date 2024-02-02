// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session

import com.intellij.platform.ml.*

/**
 * A wrapper for convenient usage of a [NestableMLSession].
 *
 * This function assumes that this session is nestable.
 */
fun <P : Any> Session<P>.withNestedSessions(useCreator: (NestableSessionWrapper<P>) -> Unit) {
  val nestableMLSession = requireNotNull(this as? NestableMLSession<P>)

  val creator = object : NestableSessionWrapper<P> {
    override fun nestConsidering(levelEnvironment: Environment): Session<P> {
      return nestableMLSession.createNestedSession(levelEnvironment)
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
fun <T, P : Any> Session<P>.withPredictions(useModelWrapper: (ModelWrapper<P>) -> T): T {
  val nestableMLSession = requireNotNull(this as? NestableMLSession<P>)
  val predictor = object : ModelWrapper<P> {
    override fun predictConsidering(predictionEnvironment: Environment): P {
      val predictionSession = nestableMLSession.createNestedSession(predictionEnvironment)
      require(predictionSession is SinglePrediction<P>)
      return predictionSession.predict()
    }

    override fun consider(predictionEnvironment: Environment) {
      val predictionSession = nestableMLSession.createNestedSession(predictionEnvironment)
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
  fun nestConsidering(levelEnvironment: Environment): Session<P>

  companion object {
    fun <P : Any> NestableSessionWrapper<P>.nestConsidering(vararg levelTierInstances: TierInstance<*>): Session<P> {
      return this.nestConsidering(Environment.of(*levelTierInstances))
    }
  }
}

interface ModelWrapper<P : Any> {
  fun predictConsidering(predictionEnvironment: Environment): P

  fun consider(predictionEnvironment: Environment)

  companion object {
    fun <P : Any> ModelWrapper<P>.predictConsidering(vararg predictionTierInstances: TierInstance<*>): P {
      return this.predictConsidering(Environment.of(*predictionTierInstances))
    }
  }
}