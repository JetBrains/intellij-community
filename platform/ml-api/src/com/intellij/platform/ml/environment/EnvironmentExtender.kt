// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.environment

import com.intellij.platform.ml.Tier
import com.intellij.platform.ml.TierInstance
import com.intellij.platform.ml.TierRequester
import com.intellij.platform.ml.with
import org.jetbrains.annotations.ApiStatus

/**
 * Provides additional tiers on the top of the main ones, making an "extended"
 * environment.
 *
 * If there are some other tiers that an [EnvironmentExtender] needs to build or access the [extendingTier],
 * then it could "order" them by defining the [requiredTiers].
 *
 * The extender will be called if and only if all the requirements are satisfied.
 *
 * Additional tiers will be later described by [com.intellij.platform.ml.TierDescriptor].
 * This description will be used for the model's inference and then logged.
 * However, they cannot be analyzed via [com.intellij.platform.ml.analysis.SessionAnalyser].
 * Because they do not make a part of the ML Task, and they could be absent in the session.
 *
 * An "extended environment" (i.e., the one that contains main as well as additional tiers) is built
 * each time when a [TierRequester] performs the desired action. For example, an [EnvironmentExtender.extend] or
 * [com.intellij.platform.ml.MLModel.Provider.provideModel] is called.
 *
 * As each extender has some requirements, as it also produces some other tier, we must first determine
 * the order in which the available extenders will run, or resolve it.
 * To lean more about that, address [com.intellij.platform.ml.environment.ExtendedEnvironment]'s documentation.
 */
@ApiStatus.Internal
interface EnvironmentExtender<T : Any> : TierRequester {
  /**
   * The tier that the extender will be providing.
   */
  val extendingTier: Tier<T>

  /**
   * Provides an instance of the [extendingTier] based on the [environment].
   *
   * @param environment includes tiers requested in [requiredTiers]
   */
  fun extend(environment: Environment): T?

  companion object {
    fun <T : Any> EnvironmentExtender<T>.extendTierInstance(environment: Environment): TierInstance<T>? {
      return extend(environment)?.let {
        this.extendingTier with it
      }
    }
  }
}
