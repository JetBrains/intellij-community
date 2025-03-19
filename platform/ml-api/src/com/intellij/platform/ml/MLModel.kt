// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.feature.FeatureSelector
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
  interface Provider<M : MLModel<P>, P : Any> {
    /**
     * Provides an ML model from the task's environment
     *
     * @param callParameters Contains any additional parameters that you passed in [com.intellij.platform.ml.MLTaskApproach.startMLSession]
     * The tier set is equal to the one that was declared in [com.intellij.platform.ml.MLTask.callParameters]'s first level.
     * @param environment Contains the all-embracing "permanent" tiers of an ML session - the ones that sit on the first position
     * of an [com.intellij.platform.ml.MLTask]'s declaration.
     * @param sessionTiers Contains the main as well as additional tiers that will be used during the session.
     * extendedPermanentSessionEnvironment
     */
    fun provideModel(callParameters: Environment, environment: Environment, sessionTiers: List<LevelTiers>): M?
  }

  /**
   * Declares a set of features, that are known and can be used by the ML model.
   * For each tier, it returns a selection.
   * Selection will be 'complete' if and only if the model could be executed with it.
   *
   * The set of tiers it is aware of could not include some additional tiers, declared
   * in [com.intellij.platform.ml.LogDrivenModelInference]'s 'not used description'.
   * Because old models could be not aware of the newly established tiers.
   */
  val knownFeatures: PerTier<FeatureSelector>

  /**
   * Performs the prediction.
   *
   * @param callParameters TODO
   * @param features Features computed for this model to take into account.
   * It is guaranteed that for each tier, this set of features is a 'complete'
   * selection of features.
   */
  fun predict(callParameters: List<Environment>, features: PerTier<Set<Feature>>): P
}
