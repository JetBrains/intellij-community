// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.environment.Environment
import org.jetbrains.annotations.ApiStatus


/**
 * An interface that represents an object, that requires some additional tiers
 * for proper functioning.
 *
 * If the requirements could not be satisfied, then the main object's functionality will not be run.
 * Please address the interface's inheritors, such as [TierDescriptor], [com.intellij.platform.ml.environment.EnvironmentExtender],
 * and [com.intellij.platform.ml.MLModel.Provider] for more details.
 */
@ApiStatus.Internal
interface TierRequester {
  /**
   * The tiers, that are required to use the object.
   */
  val requiredTiers: Set<Tier<*>>

  companion object {
    fun <T : TierRequester> Iterable<T>.fulfilledBy(environment: Environment): List<T> {
      return this.filter { it.requiredTiers.all { requiredTier -> requiredTier in environment } }
    }
  }
}
