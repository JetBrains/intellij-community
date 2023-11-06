// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.observation

import com.intellij.openapi.project.Project

object Observation {

  /**
   * Suspends until configuration processes in the IDE are completed.
   * The awaited configuration processes are those that use [ActivityTracker] or [ActivityKey]
   */
  suspend fun awaitConfiguration(project: Project, messageCallback: ((String) -> Unit)? = null) {
    // we perform several phases of awaiting here,
    // because we need to be prepared for idempotent side effects from trackers
    while (true) {
      val wasModified = awaitConfigurationPhase(project, messageCallback)
      if (wasModified) {
        messageCallback?.invoke("Configuration phase is completed. Initiating another phase to cover possible side effects...") // NON-NLS
      }
      else {
        messageCallback?.invoke("All configuration phases are completed.") // NON-NLS
        break
      }
    }
  }

  private suspend fun awaitConfigurationPhase(project: Project, messageCallback: ((String) -> Unit)?): Boolean {
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
    return ActivityTracker.EP_NAME.extensionList.map {
      object : GenericActivityTracker {
        override val name: String = it.presentableName
        override suspend fun isInProgress(): Boolean = it.isInProgress(project)
        override suspend fun awaitConfiguration() = it.awaitConfiguration(project)
      }
    }
  }
}