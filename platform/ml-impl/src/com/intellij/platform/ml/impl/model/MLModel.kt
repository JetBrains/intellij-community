// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.model

import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.TierRequester
import com.intellij.platform.ml.impl.FeatureSelector
import com.intellij.platform.ml.impl.LevelTiers
import org.jetbrains.annotations.ApiStatus

/**
 * Performs a prediction based on the given features.
 *
 * @param P The prediction's type.
 */
@ApiStatus.Internal
interface MLModel<P : Any> {
  /**
   * Provides a model, that will be used during an ML session.
   *
   * It extends [TierRequester] interface which implies, that you can request
   * additional tiers that will help you acquire the right model.
   */
  interface Provider<M : MLModel<P>, P : Any> : TierRequester {
    /**
     * Provides an ML model from the task's environment, and the additional tiers
     * declared via [requiredTiers].
     * If [requiredTiers] could not be fulfilled, then the session will not be started
     * and [com.intellij.platform.ml.impl.approach.InsufficientEnvironmentForModelProviderOutcome]
     * will be returned as the start's outcome.
     *
     * @param sessionTiers Contains the main as well as additional tiers that will be used during the session.
     * @param environment Contains the all-embracing "permanent" tiers of an ML session - the ones that sit on the first position
     * of an [com.intellij.platform.ml.impl.MLTask]'s declaration.
     * Plus additional tiers that were requested in [requiredTiers].
     * extendedPermanentSessionEnvironment
     */
    fun provideModel(sessionTiers: List<LevelTiers>, environment: Environment): M?
  }

  /**
   * Declares a set of features, that are known and can be used by the ML model.
   * For each tier, it returns a selection.
   * Selection will be 'complete' if and only if the model could be executed with it.
   *
   * The set of tiers it is aware of could not include some additional tiers, declared
   * in [com.intellij.platform.ml.impl.approach.LogDrivenModelInference]'s 'not used description'.
   * Because old models could be not aware of the newly established tiers.
   */
  val knownFeatures: PerTier<FeatureSelector>

  /**
   * Performs the prediction.
   *
   * @param features Features computed for this model to take into account.
   * It is guaranteed that for each tier, this set of features is a 'complete'
   * selection of features.
   */
  fun predict(features: PerTier<Set<Feature>>): P
}
