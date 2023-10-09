// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ObservationUtil")
package com.intellij.ide.observation

import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.openapi.observable.ActivityInProgressPredicate
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
object Observation {

  /**
   * Suspends until configuration processes in the IDE are completed.
   */
  suspend fun awaitConfiguration(project: Project, messageCallback: ((String) -> Unit)? = null) {
      while (true) {
        val wasModified = doAwaitConfigurationPredicates(project, messageCallback)
        if (!wasModified) {
          messageCallback?.invoke("The project is configured completely.") // NON-NLS
          break
        }
        else {
          messageCallback?.invoke("Modified files are saved. Checking if it triggered configuration process...") // NON-NLS
        }
      }
    }

  private suspend fun doAwaitConfigurationPredicates(project: Project, messageCallback: ((String) -> Unit)?): Boolean {
    var isModificationOccurred = false
    predicateLoop@ while (true) {
      for (processBusyPredicate in ActivityInProgressPredicate.EP_NAME.extensionList) {
        if (processBusyPredicate.isInProgress(project)) {
          isModificationOccurred = true
          messageCallback?.invoke("'${processBusyPredicate.presentableName}' is in progress...") // NON-NLS
          processBusyPredicate.awaitConfiguration(project)
          messageCallback?.invoke("'${processBusyPredicate.presentableName}' is completed.") // NON-NLS
          continue@predicateLoop
        }
      }
      break
    }

    messageCallback?.invoke("All predicates are completed. Saving files...") // NON-NLS

    saveProjectsAndApp(true)

    return isModificationOccurred
  }
}