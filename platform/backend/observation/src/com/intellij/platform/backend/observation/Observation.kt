// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.observation

import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

object Observation {

  /**
   * A flow that represents a state of configuration processes in a project.
   * Configuration process is a generic name for indexing, build system import, VFS refresh, or similar CPU-heavy activity
   * that change the readiness of the project.
   *
   * It is discouraged to perform project modification in collectors of this flow,
   * since project modifications should be *covered* by the tracking machinery, which is the core principle behind this flow.
   *
   * The values in the flow may "blink", in the sense that every VFS refresh may trigger the change of states in the flow.
   * One is advised to use [kotlinx.coroutines.flow.debounce] or similar operations to obtain proper granularity.
   *
   * @return a state flow containing `true` if the configuration process is currently running,
   * or `false` otherwise.
   */
  fun configurationFlow(project: Project): StateFlow<Boolean> = PlatformActivityTrackerService.getInstance(project).configurationFlow

  /**
   * Suspends until configuration processes in the IDE are completed.
   * The awaited configuration processes are those that use [ActivityTracker] or [ActivityKey]
   *
   * @return `true`, if some non-trivial activity was happening during the execution of this method.
   *         `false`, otherwise
   */
  suspend fun awaitConfiguration(project: Project, messageCallback: ((String) -> Unit)? = null): Boolean {
    var isModificationOccurred = false
    val extensionTrackers = collectTrackersFromExtensions(project)
    outer@ while (true) {
      // we need to reassemble all available trackers on each iteration of the outer loop,
      // since any change in the state of the system may result in a new set of available trackers
      val keyTrackers = collectTrackersFromKeys(project)
      inner@ for (tracker in keyTrackers + extensionTrackers) {
        val isInProgress = tracker.isInProgress()
        if (!isInProgress) {
          continue@inner
        }
        messageCallback?.invoke("'${tracker.name}' is in progress...")
        tracker.awaitConfiguration()
        messageCallback?.invoke("'${tracker.name}' is completed.")
        isModificationOccurred = true
        continue@outer
      }
      break
    }
    return isModificationOccurred
  }

  /**
   * Used for debugging purposes.
   * Returns stacktraces of the computations that are currently awaited by [awaitConfiguration]
   * This method affects only those computations that use [ActivityKey], whereas [ActivityTracker] is out of reach for the platform.
   */
  @ApiStatus.Internal
  fun getAllAwaitedActivities(): Set<Throwable> {
    return dumpCurrentlyObservedComputations()
  }

  private interface GenericActivityTracker {
    val name: String
    suspend fun isInProgress(): Boolean
    suspend fun awaitConfiguration()
  }

  private suspend fun collectTrackersFromKeys(project: Project): List<GenericActivityTracker> {
    val service = PlatformActivityTrackerService.getInstanceAsync(project)
    return service.getAllKeys().map {
      object : GenericActivityTracker {
        override val name: String = it.presentableName
        override suspend fun isInProgress(): Boolean = service.isInProgress(it)
        override suspend fun awaitConfiguration() = service.awaitConfiguration(it)
      }
    }
  }

  private suspend fun collectTrackersFromExtensions(project: Project): List<GenericActivityTracker> {
    return EP_NAME.extensionList.map {
      object : GenericActivityTracker {
        override val name: String = it.presentableName
        override suspend fun isInProgress(): Boolean = it.isInProgress(project)
        override suspend fun awaitConfiguration() = it.awaitConfiguration(project)
      }
    }
  }
}