// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedLevel
import com.intellij.platform.ml.impl.session.NestableStructureCollector
import com.intellij.platform.ml.impl.session.PredictionCollector
import com.intellij.platform.ml.impl.session.SessionTree
import org.jetbrains.annotations.ApiStatus

/**
 * A [SinglePrediction] performed by an ML model.
 * The session's structure is collected by [collector], after the prediction is done or canceled.
 */
@ApiStatus.Internal
class MLModelPrediction<T : SessionTree.PredictionContainer<M, DescribedLevel, P>, M : MLModel<P>, P : Any>(
  private val mlModel: M,
  private val collector: PredictionCollector<T, M, P>,
) : SinglePrediction<P> {
  override fun predict(): P {
    val prediction = mlModel.predict(collector.usableDescription)
    collector.submitPrediction(prediction)
    return prediction
  }

  override fun cancelPrediction() {
    collector.submitPrediction(null)
  }
}

/**
 * A [NestableMLSession] of utilizing [mlModel].
 * The session's structure is collected by [collector] after [onLastNestedSessionCreated],
 * and all nested sessions' structures are collected.
 */
@ApiStatus.Internal
class MLModelPredictionBranching<T : SessionTree.ChildrenContainer<M, DescribedLevel, P>, M : MLModel<P>, P : Any>(
  private val mlModel: M,
  private val collector: NestableStructureCollector<T, M, P>
) : NestableMLSession<P> {
  override fun createNestedSession(levelMainEnvironment: Environment): Session<P> {
    val nestedLevelScheme = collector.levelPositioning.lowerTiers.first()
    verifyTiersInMain(nestedLevelScheme.main, levelMainEnvironment.tiers)
    val levelAdditionalTiers = nestedLevelScheme.additional

    return if (collector.levelPositioning.lowerTiers.size == 1) {
      val nestedCollector = collector.nestPrediction(levelMainEnvironment, levelAdditionalTiers)
      MLModelPrediction(mlModel, nestedCollector)
    }
    else {
      assert(collector.levelPositioning.lowerTiers.size > 1)
      val nestedCollector = collector.nestBranch(levelMainEnvironment, levelAdditionalTiers)
      MLModelPredictionBranching(mlModel, nestedCollector)
    }
  }

  override fun onLastNestedSessionCreated() {
    collector.onLastNestedCollectorCreated()
  }
}

private fun verifyTiersInMain(expected: Set<Tier<*>>, actual: Set<*>) {
  require(expected == actual) {
    "Tier set in the main environment is not like it was declared. " +
    "Declared $expected, " +
    "but given $actual"
  }
}
