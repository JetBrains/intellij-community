// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.environment.EnvironmentExtender
import com.intellij.platform.ml.monitoring.MLApproachListener
import com.intellij.platform.ml.monitoring.MLApproachListener.Companion.asJoinedListener
import com.intellij.platform.ml.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.monitoring.MLTaskGroupListener.Companion.onAttemptedToStartSession
import com.intellij.platform.ml.monitoring.MLTaskGroupListener.Companion.targetedApproaches
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an environment, that provides extendable parts of the ML API.
 *
 * Each entity inside the API could access the platform, it is running within,
 * as everything happens after [com.intellij.platform.ml.MLTaskApproachBuilder.buildApproach],
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
   * The event will be removed via the corresponding controller's [ExtensionController.remove] call.
   * See [taskListeners].
   *
   * @return The controller, that will remove the listener on [ExtensionController.remove]
   */
  abstract fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController

  /**
   * Used to run analysis & description tasks asynchronously
   */
  abstract val coroutineScope: CoroutineScope

  /**
   * Logs API's debug information
   */
  abstract val systemLoggerBuilder: SystemLoggerBuilder

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

  interface ExtensionController {
    fun remove()
  }
}
