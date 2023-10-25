// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.observation.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.api.ActivityInProgressWitness

object Observation {

  /**
   * Suspends until configuration processes in the IDE are completed.
   */
  suspend fun awaitConfiguration(project: Project, messageCallback: ((String) -> Unit)? = null) {
    while (true) {
      val wasModified = doAwaitConfigurationPredicates(project, messageCallback)
      if (wasModified) {
        messageCallback?.invoke(
          "Configuration session is completed. Initiating another session to cover possible side effects...") // NON-NLS
      }
      else {
        messageCallback?.invoke("The project is configured completely.") // NON-NLS
        break
      }
    }
  }

  private suspend fun doAwaitConfigurationPredicates(project: Project, messageCallback: ((String) -> Unit)?): Boolean {
    var isModificationOccurred = false
    witnessLoop@ while (true) {
      for (processBusyWitness in ActivityInProgressWitness.EP_NAME.extensionList) {
        val localModificationDetected = awaitSubsystemConfiguration(project, processBusyWitness, messageCallback)
        if (localModificationDetected) {
          isModificationOccurred = true
          continue@witnessLoop
        }
      }
      break
    }

    messageCallback?.invoke("Configuration processes are completed.") // NON-NLS

    return isModificationOccurred
  }

  private suspend fun awaitSubsystemConfiguration(project: Project,
                                                  witness: ActivityInProgressWitness,
                                                  messageCallback: ((String) -> Unit)?): Boolean {
    val isInProgress = witness.isInProgress(project)
    if (!isInProgress) {
      return false
    }
    messageCallback?.invoke("'${witness.presentableName}' is in progress...")
    witness.awaitConfiguration(project)
    messageCallback?.invoke("'${witness.presentableName}' is completed.")
    return true
  }

}