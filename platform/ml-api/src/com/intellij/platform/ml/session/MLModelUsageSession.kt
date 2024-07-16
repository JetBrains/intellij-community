// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.session

import com.intellij.platform.ml.*
import com.intellij.platform.ml.ScopeEnvironment.Companion.narrowedTo
import com.intellij.platform.ml.environment.Environment

/**
 * A [SinglePrediction] performed by an ML model.
 * The session's structure is collected by [collector], after the prediction is done or canceled.
 */
internal class MLModelPrediction<T : SessionTree.PredictionContainer<M, DescribedTierData, P>, M : MLModel<P>, P : Any>(
  private val mlModel: M,
  private val collector: PredictionCollector<T, M, P>,
) : SinglePrediction<P> {
  override suspend fun predict(): P {
    val prediction = mlModel.predict(collector.callParameters, collector.usableDescription)
    collector.submitPrediction(prediction)
    return prediction
  }

  override suspend fun cancelPrediction() {
    collector.submitPrediction(null)
  }
}

/**
 * A [NestableMLSession] of utilizing [mlModel].
 * The session's structure is collected by [collector] after [onLastNestedSessionCreated],
 * and all nested sessions' structures are collected.
 */
internal class MLModelPredictionBranching<T : SessionTree.ChildrenContainer<M, DescribedTierData, P>, M : MLModel<P>, P : Any>(
  private val mlModel: M,
  private val collector: NestableStructureCollector<T, M, P>
) : NestableMLSession<P> {
  override suspend fun createNestedSession(callParameters: Environment, levelMainEnvironment: Environment): Session<P> {
    val nestedLevelCallParameters = collector.levelPositioning.lowerCallParameters.first()
    val safeCallParameters = callParameters.narrowedTo(nestedLevelCallParameters)

    val nestedLevelScheme = collector.levelPositioning.lowerTiers.first()
    verifyExactTiersSet(nestedLevelScheme.main, levelMainEnvironment.tiers, "main tiers")
    verifyExactTiersSet(nestedLevelCallParameters, callParameters.tiers, "call parameters")
    val levelAdditionalTiers = nestedLevelScheme.additional

    return if (collector.levelPositioning.lowerTiers.size == 1) {
      val nestedCollector = collector.nestPrediction(safeCallParameters, levelMainEnvironment, levelAdditionalTiers)
      MLModelPrediction(mlModel, nestedCollector)
    }
    else {
      assert(collector.levelPositioning.lowerTiers.size > 1)
      val nestedCollector = collector.nestBranch(safeCallParameters, levelMainEnvironment, levelAdditionalTiers)
      MLModelPredictionBranching(mlModel, nestedCollector)
    }
  }

  override suspend fun onLastNestedSessionCreated() {
    collector.onLastNestedCollectorCreated()
  }
}

private fun verifyExactTiersSet(expected: Set<Tier<*>>, actual: Set<*>, setName: String) {
  require(expected == actual) {
    "Tier set in $setName is not as expected." +
    "Declared $expected, " +
    "but given $actual"
  }
}
