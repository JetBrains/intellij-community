// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.apiPlatform

import com.intellij.openapi.Disposable
import com.intellij.platform.ml.*
import com.intellij.platform.ml.impl.MLTaskApproachBuilder
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.monitoring.MLApproachListener
import com.intellij.platform.ml.impl.monitoring.MLApproachListener.Companion.asJoinedListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.Companion.onAttemptedToStartSession
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener.Companion.targetedApproaches
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an environment, that provides extendable parts of the ML API.
 *
 * Each entity inside the API could access the platform, it is running within,
 * as everything happens after [com.intellij.platform.ml.impl.MLTaskApproachBuilder.buildApproach],
 * where the platform is acknowledged.
 *
 * All usages of the ij platform functionality (extension points, registry keys, etc.) shall be
 * accessed via this class.
 */
@ApiStatus.Internal
abstract class MLApiPlatform {
  /**
   * The descriptors that are available in the platform.
   * This value is interchangeable during the application runtime.
   */
  abstract val tierDescriptors: List<TierDescriptor>

  /**
   * The complete list of environment extenders, available in the platform.
   * This value is interchangeable during the application runtime.
   */
  abstract val environmentExtenders: List<EnvironmentExtender<*>>

  /**
   * The complete list of the approaches for ML tasks, available in the platform.
   * This value is interchangeable during the application runtime.
   */
  abstract val taskApproaches: List<MLTaskApproachBuilder<*>>


  /**
   * All the objects, that are listening execution of ML tasks.
   * The collection is mutable, so new listeners could be added via [addTaskListener].
   *
   * This value is mutable, new listeners could be added anytime.
   */
  abstract val taskListeners: List<MLTaskGroupListener>

  /**
   * Adds another provider for ML tasks' execution process monitoring dynamically.
   * The event will be removed via the corresponding [parentDisposable]'s [Disposable.dispose] call.
   * See [taskListeners].
   *
   * @param parentDisposable The [taskListener] will be removed when this disposable will be disposed
   */
  abstract fun addTaskListener(taskListener: MLTaskGroupListener, parentDisposable: Disposable)


  /**
   * Declares how the computed but non-declared features will be handled.
   *
   * TODO: Remove, it is useless, or add a generic "logDebug" method instead of this.
   */
  abstract fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>)

  /**
   * Used to run analysis & description tasks asynchronously
   */
  abstract val coroutineScope: CoroutineScope

  companion object {
    fun MLApiPlatform.getDescriptorsOfTiers(tiers: Set<Tier<*>>): PerTier<List<TierDescriptor>> {
      val descriptorsPerTier = tierDescriptors.groupBy { it.tier }
      return tiers.associateWith { descriptorsPerTier[it] ?: emptyList() }
    }

    fun <M : MLModel<P>, P : Any> MLApiPlatform.getJoinedListenerForTask(taskApproachBuilder: MLTaskApproachBuilder<P>,
                                                                         callEnvironment: Environment,
                                                                         permanentSessionEnvironment: Environment): MLApproachListener<M, P> {
      val relevantGroupListeners = taskListeners.filter { taskApproachBuilder.javaClass in it.targetedApproaches }
      val approachListeners = relevantGroupListeners.mapNotNull {
        it.onAttemptedToStartSession<P, M>(taskApproachBuilder, this, callEnvironment, permanentSessionEnvironment)
      }
      return approachListeners.asJoinedListener()
    }
  }
}
